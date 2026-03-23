import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

public class OctreeCopy {
    final int maximumDepth;
    final OctreeCopy[] children = new OctreeCopy[8];
    int depth;
    Vector minBox;
    Vector maxBox;
    Vector center;
    boolean isVoxel = false;
    List<Triangle> triangles;

    // -------------------------------------------------------------------------
    // Global statistics (thread-safe, shared across all nodes)
    // -------------------------------------------------------------------------
    // nodeCount[d]  = total nodes created at depth d
    // pruneCount[d] = nodes pruned (no triangle intersection) at depth d
    static final ConcurrentHashMap<Integer, AtomicInteger> nodeCount  = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Integer, AtomicInteger> pruneCount = new ConcurrentHashMap<>();

    // =========================================================================
    // Constructor
    // =========================================================================
    public OctreeCopy(int depth, Vector minBox, Vector maxBox, List<Triangle> triangles, int maximumDepth) {
        this.depth         = depth;
        this.minBox        = minBox;
        this.maxBox        = maxBox;
        this.triangles     = triangles;
        this.maximumDepth = maximumDepth;
        this.center        = new Vector(
            (minBox.x + maxBox.x) / 2f,
            (minBox.y + maxBox.y) / 2f,
            (minBox.z + maxBox.z) / 2f
        );

        // Count this node
        nodeCount.computeIfAbsent(depth, k -> new AtomicInteger(0)).incrementAndGet();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Build the OctreeCopy from the root using a ForkJoinPool (concurrency).
     * Uses Java's work-stealing thread pool — call only on the root node.
     */
    public void buildConcurrent() {
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new SubdivideTask(this));
        pool.shutdown();
    }

    /**
     * Collect all leaf voxels (isVoxel == true) by traversing the whole tree.
     */
    public List<OctreeCopy> collectVoxels() {
        List<OctreeCopy> voxels = new ArrayList<>();
        collectVoxelsRecursive(this, voxels);
        return voxels;
    }

    // =========================================================================
    // DIVIDE AND CONQUER — SUBDIVIDE
    // =========================================================================

    /**
     * Subdivide this node (Divide and Conquer step).
     *
     * Base cases:
     *   1. No triangles intersect this box → prune (skip, not a voxel)
     *   2. Reached maximum depth           → mark as voxel (leaf)
     *
     * Recursive case:
     *   Divide space into 8 octants, distribute triangles, and recurse into children.
     */
    public void subdivide() {
        // Base case 1: empty node — prune
        if (triangles == null || triangles.isEmpty()) {
            pruneCount.computeIfAbsent(depth, k -> new AtomicInteger(0)).incrementAndGet();
            return;
        }

        // Filter: only keep triangles that actually intersect this box
        List<Triangle> intersecting = filterIntersecting(triangles, minBox, maxBox);
        if (intersecting.isEmpty()) {
            pruneCount.computeIfAbsent(depth, k -> new AtomicInteger(0)).incrementAndGet();
            triangles = null;
            return;
        }

        // Base case 2: maximum depth → this node is a voxel
        if (depth >= maximumDepth) {
            isVoxel   = true;
            triangles = null; // free memory
            return;
        }

        // ----- DIVIDE -----
        // Compute the 8 child bounding boxes
        Vector[] childMins = new Vector[8];
        Vector[] childMaxs = new Vector[8];
        computeChildBounds(childMins, childMaxs);

        // ----- CONQUER -----
        // Create each child with its filtered triangle subset
        for (int i = 0; i < 8; i++) {
            List<Triangle> childTris = filterIntersecting(intersecting, childMins[i], childMaxs[i]);
            children[i] = new OctreeCopy(depth + 1, childMins[i], childMaxs[i], childTris, maximumDepth);
        }

        // Free triangles at this internal node — they live in children now
        triangles = null;
    }

    // =========================================================================
    // GEOMETRY HELPERS
    // =========================================================================

    /**
     * Compute bounding boxes for all 8 octants.
     *
     * Octant index bit encoding:
     *   bit 0 (LSB) → X: 0 = [minBox.x .. center.x],  1 = [center.x .. maxBox.x]
     *   bit 1       → Y: 0 = [minBox.y .. center.y],   1 = [center.y .. maxBox.y]
     *   bit 2       → Z: 0 = [minBox.z .. center.z],   1 = [center.z .. maxBox.z]
     */
    private void computeChildBounds(Vector[] childMins, Vector[] childMaxs) {
        float x0 = minBox.x,  y0 = minBox.y,  z0 = minBox.z;
        float cx = center.x,  cy = center.y,  cz = center.z;
        float x1 = maxBox.x,  y1 = maxBox.y,  z1 = maxBox.z;

        for (int i = 0; i < 8; i++) {
            childMins[i] = new Vector(
                ((i & 1) == 0) ? x0 : cx,
                ((i & 2) == 0) ? y0 : cy,
                ((i & 4) == 0) ? z0 : cz
            );
            childMaxs[i] = new Vector(
                ((i & 1) == 0) ? cx : x1,
                ((i & 2) == 0) ? cy : y1,
                ((i & 4) == 0) ? cz : z1
            );
        }
    }

    /**
     * Return only the triangles from {@code tris} that intersect the given AABB.
     */
    private static List<Triangle> filterIntersecting(List<Triangle> tris, Vector boxMin, Vector boxMax) {
        List<Triangle> result = new ArrayList<>();
        for (Triangle t : tris) {
            if (t.intersectsAabb(boxMin, boxMax)) {
                result.add(t);
            }
        }
        return result;
    }

    // =========================================================================
    // VOXEL COLLECTION
    // =========================================================================

    private static void collectVoxelsRecursive(OctreeCopy node, List<OctreeCopy> out) {
        if (node == null) return;
        if (node.isVoxel) {
            out.add(node);
            return;
        }
        for (OctreeCopy child : node.children) {
            collectVoxelsRecursive(child, out);
        }
    }

    // =========================================================================
    // OBJ WRITER
    // =========================================================================

    /**
     * Write all voxels to a .obj file.
     * Each voxel → 8 vertices + 12 triangular faces (a cube).
     *
     * @param voxels   list of leaf voxel nodes to write
     * @param filePath path for the output .obj file
     * @return statistics about the written geometry
     */
    public static ObjWriteResult writeVoxelsToObj(List<OctreeCopy> voxels, String filePath)
            throws java.io.IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("# Voxelized OBJ output\n");
        sb.append("# Generated by Octree Voxelizer\n\n");

        int vertexOffset = 1; // OBJ vertex indices are 1-based
        int faceCount    = 0;

        for (OctreeCopy voxel : voxels) {
            Vector lo = voxel.minBox;
            Vector hi = voxel.maxBox;

            // 8 corners of the cube (local indices 0-7 → global o..o+7):
            //  0: lo.x lo.y lo.z
            //  1: hi.x lo.y lo.z
            //  2: hi.x hi.y lo.z
            //  3: lo.x hi.y lo.z
            //  4: lo.x lo.y hi.z
            //  5: hi.x lo.y hi.z
            //  6: hi.x hi.y hi.z
            //  7: lo.x hi.y hi.z
            sb.append(String.format("v %f %f %f%n", lo.x, lo.y, lo.z));
            sb.append(String.format("v %f %f %f%n", hi.x, lo.y, lo.z));
            sb.append(String.format("v %f %f %f%n", hi.x, hi.y, lo.z));
            sb.append(String.format("v %f %f %f%n", lo.x, hi.y, lo.z));
            sb.append(String.format("v %f %f %f%n", lo.x, lo.y, hi.z));
            sb.append(String.format("v %f %f %f%n", hi.x, lo.y, hi.z));
            sb.append(String.format("v %f %f %f%n", hi.x, hi.y, hi.z));
            sb.append(String.format("v %f %f %f%n", lo.x, hi.y, hi.z));

            int o = vertexOffset;
            // Bottom face (z = lo.z)
            sb.append(String.format("f %d %d %d%n", o,   o+1, o+2));
            sb.append(String.format("f %d %d %d%n", o,   o+2, o+3));
            // Top face    (z = hi.z)
            sb.append(String.format("f %d %d %d%n", o+4, o+6, o+5));
            sb.append(String.format("f %d %d %d%n", o+4, o+7, o+6));
            // Front face  (y = lo.y)
            sb.append(String.format("f %d %d %d%n", o,   o+5, o+1));
            sb.append(String.format("f %d %d %d%n", o,   o+4, o+5));
            // Back face   (y = hi.y)
            sb.append(String.format("f %d %d %d%n", o+3, o+2, o+6));
            sb.append(String.format("f %d %d %d%n", o+3, o+6, o+7));
            // Left face   (x = lo.x)
            sb.append(String.format("f %d %d %d%n", o,   o+3, o+7));
            sb.append(String.format("f %d %d %d%n", o,   o+7, o+4));
            // Right face  (x = hi.x)
            sb.append(String.format("f %d %d %d%n", o+1, o+6, o+2));
            sb.append(String.format("f %d %d %d%n", o+1, o+5, o+6));

            vertexOffset += 8;
            faceCount    += 12;
        }

        java.nio.file.Files.writeString(java.nio.file.Path.of(filePath), sb.toString());

        return new ObjWriteResult(voxels.size(), vertexOffset - 1, faceCount);
    }

    // =========================================================================
    // BOUNDING BOX UTILITY
    // =========================================================================

    /**
     * Compute the axis-aligned bounding box of all triangles.
     * Returns [minCorner, maxCorner] with a small epsilon padding.
     */
    public static Vector[] computeBoundingBox(List<Triangle> triangles) {
        float minX = Float.MAX_VALUE,  minY = Float.MAX_VALUE,  minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (Triangle t : triangles) {
            for (Vector v : new Vector[]{t.v1, t.v2, t.v3}) {
                if (v.x < minX) minX = v.x;
                if (v.y < minY) minY = v.y;
                if (v.z < minZ) minZ = v.z;
                if (v.x > maxX) maxX = v.x;
                if (v.y > maxY) maxY = v.y;
                if (v.z > maxZ) maxZ = v.z;
            }
        }

        // Small epsilon so surface triangles on the boundary are included
        float eps = 1e-4f;
        return new Vector[]{
            new Vector(minX - eps, minY - eps, minZ - eps),
            new Vector(maxX + eps, maxY + eps, maxZ + eps)
        };
    }

    // =========================================================================
    // INNER CLASS — ForkJoin task for concurrent subdivide
    // =========================================================================

    /**
     * RecursiveAction that drives the concurrent divide-and-conquer subdivision.
     *
     * Pattern:
     *   1. Subdivide the current node (creates children).
     *   2. Fork a separate task for each child in parallel.
     *   3. Join all child tasks before returning.
     *
     * ForkJoinPool's work-stealing ensures CPU cores stay busy even when some
     * subtrees are much larger than others.
     */
    private static class SubdivideTask extends RecursiveAction {
        private final Octree node;

        SubdivideTask(Octree node) {
            this.node = node;
        }

        @Override
        protected void compute() {
            // Subdivide this node (may create children)
            node.subdivide();

            // Fork a task for every created child
            List<SubdivideTask> childTasks = new ArrayList<>(8);
            for (Octree child : node.children) {
                if (child != null) {
                    childTasks.add(new SubdivideTask(child));
                }
            }

            // invokeAll forks all tasks and blocks until they all finish
            invokeAll(childTasks);
        }
    }

    // =========================================================================
    // INNER CLASS — OBJ write result (statistics)
    // =========================================================================

    public static class ObjWriteResult {
        public final int voxelCount;
        public final int vertexCount;
        public final int faceCount;

        public ObjWriteResult(int voxelCount, int vertexCount, int faceCount) {
            this.voxelCount  = voxelCount;
            this.vertexCount = vertexCount;
            this.faceCount   = faceCount;
        }
    }
}
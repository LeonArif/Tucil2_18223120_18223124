import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

/**
 * Entry point for the 3D Voxelizer.
 *
 * Usage:
 *   java Main <path-to-input.obj> <max-depth>
 *
 * Example:
 *   java Main models/pumpkin.obj 5
 */
public class MainCopy {

    public static void main(String[] args) {
        // ------------------------------------------------------------------
        // 1. Argument validation
        // ------------------------------------------------------------------
        if (args.length != 2) {
            System.err.println("Usage: java Main <path-to-input.obj> <max-depth>");
            System.exit(1);
        }

        String inputPath = args[0];
        int maxDepth;
        try {
            maxDepth = Integer.parseInt(args[1]);
            if (maxDepth < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("Error: max-depth must be a positive integer.");
            System.exit(1);
            return;
        }

        // ------------------------------------------------------------------
        // 2. Parse input .obj
        // ------------------------------------------------------------------
        ObjParser parser = new ObjParser();
        ObjParser.ParseResult parsed;
        try {
            parsed = parser.parse(inputPath);
        } catch (Exception e) {
            System.err.println("Error reading/parsing OBJ file: " + e.getMessage());
            System.exit(1);
            return;
        }

        List<Triangle> triangles = parsed.getTriangles();
        System.out.println("=== 3D Voxelizer ===");
        System.out.printf("Input file    : %s%n", inputPath);
        System.out.printf("Vertices      : %d%n", parsed.getVertices().size());
        System.out.printf("Faces (input) : %d%n", parsed.getFaces().size());
        System.out.printf("Triangles     : %d%n", triangles.size());
        System.out.printf("Max depth     : %d%n%n", maxDepth);

        // ------------------------------------------------------------------
        // 3. Compute root bounding box
        // ------------------------------------------------------------------
        Vector[] bbox = Octree.computeBoundingBox(triangles);
        Vector rootMin = bbox[0];
        Vector rootMax = bbox[1];

        // Make the root box a cube (equal side lengths) to get uniform voxels
        float sizeX = rootMax.x - rootMin.x;
        float sizeY = rootMax.y - rootMin.y;
        float sizeZ = rootMax.z - rootMin.z;
        float maxSize = Math.max(sizeX, Math.max(sizeY, sizeZ));
        float padX = (maxSize - sizeX) / 2f;
        float padY = (maxSize - sizeY) / 2f;
        float padZ = (maxSize - sizeZ) / 2f;
        rootMin = new Vector(rootMin.x - padX, rootMin.y - padY, rootMin.z - padZ);
        rootMax = new Vector(rootMin.x + maxSize, rootMin.y + maxSize, rootMin.z + maxSize);

        // ------------------------------------------------------------------
        // 4. Build Octree (concurrent divide and conquer)
        // ------------------------------------------------------------------
        // Reset global stats from any previous runs
        Octree.nodeCount.clear();
        Octree.pruneCount.clear();

        Octree root = new Octree(0, rootMin, rootMax, triangles, maxDepth);

        long startTime = System.currentTimeMillis();
        root.buildConcurrent();
        long endTime = System.currentTimeMillis();
        long elapsedMs = endTime - startTime;

        // ------------------------------------------------------------------
        // 5. Collect voxels
        // ------------------------------------------------------------------
        List<Octree> voxels = root.collectVoxels();

        // ------------------------------------------------------------------
        // 6. Write output .obj
        // ------------------------------------------------------------------
        String outputPath = deriveOutputPath(inputPath);
        Octree.ObjWriteResult writeResult;
        try {
            writeResult = Octree.writeVoxelsToObj(voxels, outputPath);
        } catch (Exception e) {
            System.err.println("Error writing output OBJ: " + e.getMessage());
            System.exit(1);
            return;
        }

        // ------------------------------------------------------------------
        // 7. Print statistics
        // ------------------------------------------------------------------
        int actualDepth = Octree.nodeCount.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        System.out.printf("Voxels formed      : %d%n",  writeResult.voxelCount);
        System.out.printf("Vertices formed    : %d%n",  writeResult.vertexCount);
        System.out.printf("Faces formed       : %d%n",  writeResult.faceCount);
        System.out.printf("Octree depth       : %d%n",  actualDepth);
        System.out.printf("Execution time     : %d ms%n", elapsedMs);
        System.out.printf("Output file        : %s%n%n", Path.of(outputPath).toAbsolutePath());

        System.out.println("--- Octree nodes per depth ---");
        new TreeMap<>(Octree.nodeCount).forEach((depth, count) ->
            System.out.printf("  %2d : %d%n", depth, count.get())
        );

        System.out.println();
        System.out.println("--- Pruned nodes per depth (no intersection) ---");
        new TreeMap<>(Octree.pruneCount).forEach((depth, count) ->
            System.out.printf("  %2d : %d%n", depth, count.get())
        );
    }

    // ------------------------------------------------------------------
    // Helper: derive output path from input path
    //   e.g. "models/pumpkin.obj" → "models/pumpkin-voxelized.obj"
    // ------------------------------------------------------------------
    private static String deriveOutputPath(String inputPath) {
        Path p = Path.of(inputPath);
        String fileName = p.getFileName().toString();
        String baseName = fileName.endsWith(".obj")
            ? fileName.substring(0, fileName.length() - 4)
            : fileName;
        String outName = baseName + "-voxelized.obj";
        Path parent = p.getParent();
        return (parent == null) ? outName : parent.resolve(outName).toString();
    }
}
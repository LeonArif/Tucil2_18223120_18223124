public class Triangle {
    Vector v1;
    Vector v2;
    Vector v3;

    public Triangle(Vector v1, Vector v2, Vector v3){
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    public Vector getNormal() {
        Vector side1 = v2.subtract(v1);
        Vector side2 = v3.subtract(v1);
        Vector n = side1.cross(side2);
        return n; 
    }

    public Vector getCentroid(){
        float x = (v1.x + v2.x + v3.x) / 3f;
        float y = (v1.y + v2.y + v3.y) / 3f;
        float z = (v1.z + v2.z + v3.z) / 3f;
        return new Vector(x, y, z);
    }

    public float getArea() {
        Vector cross = v2.subtract(v1).cross(v3.subtract(v1));
        float crossMagnitude = (float) Math.sqrt(cross.dot(cross));
        return 0.5f * crossMagnitude;
    }

    public Vector getAabbMin() {
        float minX = Math.min(v1.x, Math.min(v2.x, v3.x));
        float minY = Math.min(v1.y, Math.min(v2.y, v3.y));
        float minZ = Math.min(v1.z, Math.min(v2.z, v3.z));
        return new Vector(minX, minY, minZ);
    }

    public Vector getAabbMax() {
        float maxX = Math.max(v1.x, Math.max(v2.x, v3.x));
        float maxY = Math.max(v1.y, Math.max(v2.y, v3.y));
        float maxZ = Math.max(v1.z, Math.max(v2.z, v3.z));
        return new Vector(maxX, maxY, maxZ);
    }

    public boolean intersectsAabb(Vector boxMin, Vector boxMax) {
        Vector triMin = getAabbMin();
        Vector triMax = getAabbMax();

        boolean overlapX = triMax.x >= boxMin.x && triMin.x <= boxMax.x;
        boolean overlapY = triMax.y >= boxMin.y && triMin.y <= boxMax.y;
        boolean overlapZ = triMax.z >= boxMin.z && triMin.z <= boxMax.z;

        return overlapX && overlapY && overlapZ;
    }
}

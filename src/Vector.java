public class Vector {
    public float x;
    public float y;
    public float z;

    public Vector(float x,float y,float z){
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector add(Vector other){
        return new Vector(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vector subtract(Vector other){
        return new Vector(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vector multiply(float k){
        return new Vector(this.x * k, this.y * k, this.z * k);
    }

    public Vector cross(Vector other) {
        return new Vector(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
        );
    }

    public float dot(Vector other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }
}

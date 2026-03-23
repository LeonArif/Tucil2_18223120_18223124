import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ObjParser {

	public static class ParseResult {
		private final List<Vector> vertices;
		private final List<int[]> faces;
		private final List<Triangle> triangles;

		public ParseResult(List<Vector> vertices, List<int[]> faces, List<Triangle> triangles) {
			this.vertices = vertices;
			this.faces = faces;
			this.triangles = triangles;
		}

		public List<Vector> getVertices() {
			return vertices;
		}

		public List<int[]> getFaces() {
			return faces;
		}

		public List<Triangle> getTriangles() {
			return triangles;
		}
	}

	public ParseResult parse(String filePath) throws IOException {
		return parse(Path.of(filePath));
	}

	public ParseResult parse(Path filePath) throws IOException {
		List<Vector> vertices = new ArrayList<>();
		List<int[]> faces = new ArrayList<>();

		try (BufferedReader reader = Files.newBufferedReader(filePath)) {
			String line;
			int lineNumber = 0;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				String trimmed = line.trim();

				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}

				String[] tokens = trimmed.split("\\s+");
				if (tokens.length == 0) {
					continue;
				}

				if ("v".equals(tokens[0])) {
					parseVertex(tokens, lineNumber, vertices);
				} else if ("f".equals(tokens[0])) {
					parseFace(tokens, lineNumber, vertices.size(), faces);
				} else {
					throw new IllegalArgumentException(
						"Baris " + lineNumber + " tidak valid. Hanya mendukung baris 'v x y z' atau 'f i j k'."
					);
				}
			}
		}

		if (vertices.isEmpty()) {
			throw new IllegalArgumentException("File OBJ tidak memiliki vertex.");
		}
		if (faces.isEmpty()) {
			throw new IllegalArgumentException("File OBJ tidak memiliki face.");
		}

		List<Triangle> triangles = buildTriangles(vertices, faces);

		return new ParseResult(
			Collections.unmodifiableList(vertices),
			Collections.unmodifiableList(faces),
			Collections.unmodifiableList(triangles)
		);
	}

	private void parseVertex(String[] tokens, int lineNumber, List<Vector> vertices) {
		if (tokens.length != 4) {
			throw new IllegalArgumentException(
				"Baris " + lineNumber + " tidak valid untuk vertex. Format harus: v x y z"
			);
		}

		try {
			float x = Float.parseFloat(tokens[1]);
			float y = Float.parseFloat(tokens[2]);
			float z = Float.parseFloat(tokens[3]);
			vertices.add(new Vector(x, y, z));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
				"Baris " + lineNumber + " memiliki koordinat vertex tidak valid.",
				e
			);
		}
	}

	private void parseFace(String[] tokens, int lineNumber, int vertexCount, List<int[]> faces) {
		if (tokens.length != 4) {
			throw new IllegalArgumentException(
				"Baris " + lineNumber + " tidak valid untuk face. Format harus: f i j k"
			);
		}

		try {
			int i = Integer.parseInt(tokens[1]);
			int j = Integer.parseInt(tokens[2]);
			int k = Integer.parseInt(tokens[3]);

			validateFaceIndex(i, vertexCount, lineNumber);
			validateFaceIndex(j, vertexCount, lineNumber);
			validateFaceIndex(k, vertexCount, lineNumber);

			faces.add(new int[] { i, j, k });
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
				"Baris " + lineNumber + " memiliki indeks face tidak valid.",
				e
			);
		}
	}

	private void validateFaceIndex(int index, int vertexCount, int lineNumber) {
		if (index < 1 || index > vertexCount) {
			throw new IllegalArgumentException(
				"Baris " + lineNumber + " memiliki indeks face di luar rentang vertex: " + index
			);
		}
	}

	private List<Triangle> buildTriangles(List<Vector> vertices, List<int[]> faces) {
		List<Triangle> triangles = new ArrayList<>(faces.size());

		for (int[] face : faces) {
			Vector v1 = vertices.get(face[0] - 1);
			Vector v2 = vertices.get(face[1] - 1);
			Vector v3 = vertices.get(face[2] - 1);
			triangles.add(new Triangle(v1, v2, v3));
		}

		return triangles;
	}
}
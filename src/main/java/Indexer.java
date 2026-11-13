import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class Indexer {

    // Crea l'analyzer per i campi "nome" e "contenuto" (come visto a lezione)
    public static Analyzer getAnalyzer() throws IOException {
        Analyzer nomeAnalyzer = CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("wordDelimiterGraph")
                .addTokenFilter("lowercase")
                .build();

        Map<String, Analyzer> perField = new HashMap<>();
        perField.put("nome", nomeAnalyzer);
        perField.put("contenuto", new EnglishAnalyzer());

        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), perField);
    }

    // Indicizza i primi maxFiles file .txt della cartella e restituisce il tempo in ms
    public static long indexDocs(Path docsPath, Path indexPath, int maxFiles) throws IOException {
        File[] files = docsPath.toFile().listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            System.out.println("Nessun file trovato in " + docsPath);
            return 0;
        }
        Arrays.sort(files);

        long start = System.currentTimeMillis();

        try (Directory directory = FSDirectory.open(indexPath)) {
            IndexWriterConfig config = new IndexWriterConfig(getAnalyzer());
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.deleteAll();

                int limit = Math.min(files.length, maxFiles);
                for (int i = 0; i < limit; i++) {
                    File f = files[i];
                    String contenuto = Files.readString(f.toPath());

                    Document doc = new Document();
                    doc.add(new TextField("nome", f.getName(), Field.Store.YES));
                    doc.add(new TextField("contenuto", contenuto, Field.Store.NO));
                    writer.addDocument(doc);
                }
                writer.commit();
            }
        }

        return System.currentTimeMillis() - start;
    }

    public static void main(String[] args) throws Exception {
        Path docsPath = Paths.get("data/corpus");
        Path indexPath = Paths.get("target/idx");

        System.out.println("=== TEST TEMPI DI INDICIZZAZIONE AL VARIARE DEI DOCUMENTI ===");
        int[] steps = {100, 500, 1000, 2500, 5000, 10000};
        for (int n : steps) {
            long timeMs = indexDocs(docsPath, indexPath, n);
            System.out.println("Documenti indicizzati: " + n + " -> Tempo: " + timeMs + " ms");
        }
        System.out.println("\nIndice finale di 10.000 documenti pronto in: " + indexPath.toAbsolutePath());
    }
}
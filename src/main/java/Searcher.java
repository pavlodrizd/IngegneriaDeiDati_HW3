import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class Searcher {

    // Esegue il parsing della query che inizia con "nome" o "contenuto"
    public static Query parseConsoleQuery(String line, Analyzer analyzer) throws Exception {
        String[] parts = line.trim().split("\\s+", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Formato query: <nome|contenuto> <termini o \"frase\">");
        }

        String field = parts[0].toLowerCase();
        if (field.equals("contentuto")) {
            field = "contenuto"; // gestisce il refuso nella traccia
        }
        if (!field.equals("nome") && !field.equals("contenuto")) {
            throw new IllegalArgumentException("La query deve iniziare con 'nome' o 'contenuto'");
        }

        QueryParser parser = new QueryParser(field, analyzer);
        return parser.parse(parts[1]);
    }

    // Esegue in automatico le 10 query di test e calcola Precision, Recall e F1
    public static void runTestQueries(IndexSearcher searcher, Analyzer analyzer) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("data/ground_truth.tsv"));
        StoredFields storedFields = searcher.storedFields();

        System.out.println("\n=== RISULTATI DELLE 10 QUERY DI TEST ===");
        System.out.println("ID\tQuery\t\t\t\tRilevanti(GT)\tTrovati\tTP\tPrecision\tRecall\tF1\tTempo(ms)");

        for (String line : lines) {
            String[] cols = line.split("\t");
            String qid = cols[0];
            String queryText = cols[1];
            Set<String> groundTruth = new HashSet<>(Arrays.asList(cols[2].split(",")));

            long t0 = System.nanoTime();
            Query query = parseConsoleQuery(queryText, analyzer);
            TopDocs hits = searcher.search(query, 10000);
            double timeMs = (System.nanoTime() - t0) / 1_000_000.0;

            int tp = 0;
            for (ScoreDoc sd : hits.scoreDocs) {
                Document doc = storedFields.document(sd.doc);
                if (groundTruth.contains(doc.get("nome"))) {
                    tp++;
                }
            }

            int retrieved = hits.scoreDocs.length;
            int relevant = groundTruth.size();
            double precision = retrieved > 0 ? (double) tp / retrieved : 0;
            double recall = relevant > 0 ? (double) tp / relevant : 0;
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;

            System.out.printf(Locale.US, "%s\t%-35s\t%d\t%d\t%d\t%.3f\t\t%.3f\t%.3f\t%.2f ms%n",
                    qid, queryText, relevant, retrieved, tp, precision, recall, f1, timeMs);
        }
    }

    public static void main(String[] args) throws Exception {
        Path indexPath = Paths.get("target/idx");
        Analyzer analyzer = Indexer.getAnalyzer();

        try (Directory directory = FSDirectory.open(indexPath);
             IndexReader reader = DirectoryReader.open(directory);
             Scanner scanner = new Scanner(System.in)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            StoredFields storedFields = searcher.storedFields();

            // Se lanciato con argomento --test, stampa subito la tabella delle 10 query
            if (args.length > 0 && args[0].equals("--test")) {
                runTestQueries(searcher, analyzer);
                return;
            }

            System.out.println("Indice aperto (" + reader.numDocs() + " file).");
            System.out.println("Scrivi una query (es. 'nome lunar' o 'contenuto \"magnetic resonance imaging\"'), 'test' per le 10 query, o 'esci':");

            while (true) {
                System.out.print("\n> ");
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("esci")) break;
                if (input.isEmpty()) continue;

                if (input.equalsIgnoreCase("test")) {
                    runTestQueries(searcher, analyzer);
                    continue;
                }

                try {
                    long start = System.currentTimeMillis();
                    Query query = parseConsoleQuery(input, analyzer);
                    TopDocs hits = searcher.search(query, 10);
                    long elapsed = System.currentTimeMillis() - start;

                    System.out.println("Trovati " + hits.totalHits.value() + " documenti in " + elapsed + " ms (mostrati top 10):");
                    for (int i = 0; i < hits.scoreDocs.length; i++) {
                        ScoreDoc scoreDoc = hits.scoreDocs[i];
                        Document doc = storedFields.document(scoreDoc.doc);
                        System.out.println("doc" + scoreDoc.doc + ": " + doc.get("nome") + " (" + scoreDoc.score + ")");
                    }
                } catch (Exception e) {
                    System.out.println("Errore: " + e.getMessage());
                }
            }
        }
    }
}
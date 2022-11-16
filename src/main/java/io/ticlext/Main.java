package io.ticlext;

import Atom.Encoding.Encoder;
import Atom.Encoding.EncoderJson;
import Atom.Time.Time;
import Atom.Time.Timer;
import Atom.Utility.Pool;
import Atom.Utility.Utility;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {
    public static final String baseURL = "https://www.paginegialle.it/";
    static final Properties headers = new Properties();
    public static ThreadLocal<Properties> headersSupplier;
    public static boolean suggest = true;
    
    static {
        headers.setProperty("User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.80 Safari/537.36");
        headers.setProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.setProperty("Accept-Language", "en-US,en;q=0.9");
        headers.setProperty("Upgrade-Insecure-Requests", "1");
        headersSupplier = ThreadLocal.withInitial(() -> {
            Properties p = new Properties();
            p.putAll(headers);
            return p;
        });
    }
    
    public static void main(String[] args) throws Exception {
        String httpsDef = "https://www.paginegialle.it/marche/ancona/associazioni_sindacali_e_di_categoria.html";
        String[] https;
        if (args.length > 0){
            https = args;//lol
        }else{
            https = Utility.input("Location URL [" + httpsDef + "] comma separated", null, ss -> {
                if (ss.length() == 0){
                    ss = httpsDef;
                }
                return ss.split(",");
            });
        }
        //validation
        for (String s : https) {
            if (!s.endsWith(".html")){
                System.err.println(s);
                System.err.println("Expected to end with '.html'");
                return;//sayonara
            }
        }
        List<Future<?>> futures = new ArrayList<>();
        for (String http : https) {
            futures.add(Pool.async(() -> {
                URL url = toURL(http, 0);//Real magic here
                try {
                    getHTTP(url);//basic fetch
                    parseLoop(http);//JSON fetch
                }catch(IOException e){
                    System.err.println("Not found for: " + http + " " + e);
                }
            }));
        }
        Time start = new Time(TimeUnit.SECONDS);
        Timer timer = new Timer(TimeUnit.SECONDS, 5);
        ThreadPoolExecutor executor = null;
        if (Pool.parallelAsync instanceof ThreadPoolExecutor){
            executor = (ThreadPoolExecutor) Pool.parallelAsync;
        }
        while (!futures.isEmpty()) {
            Thread.sleep(1000);
            futures.removeIf(Future::isDone);
            if (timer.get()){
                System.out.println("Remaining: " + futures.size() + " - Time Elapsed: " + start.elapsedS());
            }
            if (executor != null){
                int before = executor.getMaximumPoolSize();
                int after = Pool.getPossibleThreadCount(executor);
                executor.setMaximumPoolSize(after);
                if (before != after){
                    System.out.println((before > after ? "Decreased" : "Increased") + " threads count from " + before + " to " + after);
                }
            }
        }
    }
    
    private static URL toURL(String http, int index) {
        // input: https://www.paginegialle.it/lazio/roma/bar.html
        // output: https://www.paginegialle.it/lazio/roma/bar/p-0.html?output=json
        try {
            if (http.endsWith(".html")){
                http = http.substring(0, http.length() - 5);
            }
            return new URL(http + "/p-" + index + ".html?output=json");
            
        }catch(MalformedURLException e){
            throw new RuntimeException(e);
        }
    }
    
    public static void parseLoop(String url) {
        URL url1 = null;
        try {
            url1 = new URL(url);
        }catch(MalformedURLException e){
            e.printStackTrace();
            return;
        }
        String fileName = url1.getFile();
        fileName = fileName.replace(".html", "");
        File file = new File("data/" + fileName + ".csv");
        if (file.exists()){
            System.out.println("File exist skipping: " + file);
            return;
        }
        long resultCount = 0;
        file.getAbsoluteFile().getParentFile().mkdirs();
        
        int maxPage = 1;
        int page = 0;
        System.out.println("[" + fileName + "]: " + file.getAbsolutePath());
        url1 = toURL(url, page);
        try(CSVWriter csvWriter = new CSVWriter(new PrintWriter(file))) {
            csvWriter.writeNext(Result.csvHeader());
            csvWriter.flushQuietly();
            while (!Thread.interrupted() && url1 != null) {
                try {
                    JsonElement json = parseJson(url1);
                    url1 = null;
                    List<Result> results = Result.parse(json);
                    resultCount += results.size();
                    for (Result result : results) {
                        csvWriter.writeNext(result.csvData());
                    }
                    csvWriter.flushQuietly();
                    JsonObject pagination = json.getAsJsonObject()
                            .get("list")
                            .getAsJsonObject()
                            .get("pagination")
                            .getAsJsonObject();
                    maxPage = pagination.get("numPages").getAsInt();
                    page = pagination.get("currentPage").getAsInt();
                    if (page < maxPage){
                        url1 = toURL(url, page + 1);
                    }
                    System.out.println("[" + fileName + "]: " + page + "/" + maxPage + " " + resultCount);
                }catch(IOException e){
                    System.err.println("[" + fileName + "]: " + page + "/" + maxPage + " " + resultCount + " " + e.getMessage());
                    page++;
                    if (page < maxPage){
                        url1 = toURL(url, page + 1);
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public static void parseLoop(URL url, String category, String loc) {
        File file = new File("data/" + loc + "/" + category + ".csv");
        long resultCount = 0;
        file.getAbsoluteFile().getParentFile().mkdirs();
        System.out.println("[" + category + "][" + loc + "]: " + file.getAbsolutePath());
        int maxPage = 1;
        int page = 1;
        try(CSVWriter csvWriter = new CSVWriter(new PrintWriter(file))) {
            csvWriter.writeNext(Result.csvHeader());
            csvWriter.flushQuietly();
            while (!Thread.interrupted() && url != null) {
                try {
                    JsonElement json = parseJson(url);
                    url = null;
                    List<Result> results = Result.parse(json);
                    resultCount += results.size();
                    for (Result result : results) {
                        csvWriter.writeNext(result.csvData());
                    }
                    csvWriter.flushQuietly();
                    JsonObject pagination = json.getAsJsonObject()
                            .get("list")
                            .getAsJsonObject()
                            .get("pagination")
                            .getAsJsonObject();
                    maxPage = pagination.get("numPages").getAsInt();
                    page = pagination.get("currentPage").getAsInt();
                    if (page < maxPage){
                        url = toURL(category, loc, page + 1);
                    }
                    System.out.println("[" + category + "][" + loc + "]: " + page + "/" + maxPage + " " + resultCount);
                }catch(IOException e){
                    System.err.println("[" + category + "][" + loc + "] Error: " + e);
                    page++;
                    if (page < maxPage){
                        url = toURL(category, loc, page + 1);
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("[" + category + "][" + loc + "] Result count: " + resultCount);
    }
    
    public static JsonElement parseJson(URL url) throws IOException {
        return EncoderJson.parseJson(getHTTP(url));
    }
    
    public static URL toURL(String category, String loc, int i) throws MalformedURLException {
        return new URL("https://www.paginegialle.it/" + category + "/" + loc + "/p-" + i + "?" + (suggest ? "suggest=true&" : "") + "output=json");
    }
    
    public static String getHTTP(URL url) throws IOException {
        
        
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        final Properties head = headersSupplier.get();
        for (Object o : head.keySet()) {
            String key = String.valueOf(o);
            con.setRequestProperty(key, head.getProperty(key));
        }
        
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        con.setDoInput(true);
        con.connect();
        String cache = con.getHeaderField("Set-Cookie");
        if (!head.containsKey("Cookie") && cache != null){
            head.setProperty("Cookie", cache);
        }
        return Encoder.readString(con.getInputStream());
        
    }
    
}

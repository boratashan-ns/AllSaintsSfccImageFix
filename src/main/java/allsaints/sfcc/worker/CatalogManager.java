package allsaints.sfcc.worker;


import allsaints.sfcc.worker.model.CatalogExportJobResponse;
import allsaints.sfcc.worker.model.JobsItem;
import allsaints.sfcc.worker.model.NwsAccessToken;
import allsaints.sfcc.worker.model.NwsSFCCJobHistory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/*
	1) Start
	2) Read SFCC catalog import jobs
		a. Find the recent app upload
		b. Get the original import datetime
		c. Find the next import by import date time
			i. If not then break
	3) Download the file to the temporary folder
	4) Extract it
	5) Convert files
	6) Compress files
	7) Run new import job (add Catalog import date to job id)
	8) Upload the file
Finish
 */

public class CatalogManager {

    private static final Logger logger = LoggerFactory.getLogger(CatalogManager.class);
    private static final String SITE_ID = "SFCC-Catalog-Workaround-tool";
    private static final String OP_FILE_PREFIX = "AllsaintsSfccWorkerCommandCatalog";
    private String TOKEN = "";
    private Gson gson;
    private String host = "";
    private String userName;
    private String password;
    private boolean forceFirstTimeRun;

    private HttpClient httpClient;

    private CatalogManager() {

    }

    public static CatalogManager create(CatalogManagerConfig config) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        CatalogManager manager = new CatalogManager();
        manager.host = String.format("https://%s.p.newstore.net",  config.getTenant());
        manager.userName = config.getUserName();
        manager.password = config.getPassword();
        manager.forceFirstTimeRun = config.isForceFirstTimeRun();
        manager.httpClient = HttpClient.newHttpClient();
        manager.gson = new GsonBuilder().create();
        return manager;

    }


    public void obtainToken() throws IOException, InterruptedException, CatalogManagerException {
        logger.info("Obtaining token...");
        String body = String.format("username=%s&grant_type=password&password=%s",
                userName, password);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/v0/token", host)))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            String s = response.body();
            NwsAccessToken token = this.gson.fromJson(s, NwsAccessToken.class);
            this.TOKEN = token.getAccess_token();
        } else {
            throw new CatalogManagerException(String.format("[method obtainToken] API Error StatusCode %d, Body : %s", response.statusCode(), response.body()));
        }
        logger.info("Obtaining token[DONE]");
    }


    public Optional<NwsSFCCJobHistory> readImports() throws IOException, InterruptedException, CatalogManagerException {
        logger.info("Reading imports...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/sfcc-api/v1/job_history?entity=catalog&status=finished", this.host)))
                .setHeader("Authorization", String.format("Bearer %s", this.TOKEN))
                .GET()
                .build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String s = response.body();
            NwsSFCCJobHistory jobHistory = gson.fromJson(s, NwsSFCCJobHistory.class);
            logger.info("Reading imports[DONE]");
            return Optional.of(jobHistory);
        } else {
            throw new CatalogManagerException(String.format("[method readImports] API Error StatusCode %d, Body : %s", response.statusCode(), response.body()));
        }
    }

    public Optional<JobsItem> getImportTaskCandidate(NwsSFCCJobHistory jobHistory) {
        if (forceFirstTimeRun) {
            return Optional.of(jobHistory.getJobs().get(0));
        }
        int j =0;
        for(JobsItem jobsItem : jobHistory.getJobs()) {
                if (Objects.nonNull(jobsItem.getMetadata().getSiteId())) {
                    if (jobsItem.getMetadata().getSiteId().equalsIgnoreCase(CatalogManager.SITE_ID)) {
                        OffsetDateTime sourceDateTime = OffsetDateTime.parse(jobsItem.getMetadata().getJobId());
                        int size = jobHistory.getJobs().size();
                        for (int i = j; i >= 0; i--) {
                            OffsetDateTime importDateTime = OffsetDateTime.parse(jobHistory.getJobs().get(i).getCreatedAt());
                            if (importDateTime.isAfter(sourceDateTime)) {
                                logger.info(String.format("A new import has been found, the last import is on %s, the new one is on %s",
                                        sourceDateTime,
                                        importDateTime));
                                return Optional.of(jobHistory.getJobs().get(i));
                            }
                        }
                    }
                }
                j++;
            }
            return Optional.empty();
    }

    public File downloadsInputFile(JobsItem jobsItem) throws IOException, InterruptedException, CatalogManagerException {
        logger.info("Downloads import file from SFCC...");
        String input = jobsItem.getLinks().getInput();
        String s3Location = this.getS3FileFromNewstore(String.format("%s%s", this.host, input));
        logger.info(String.format("Downloading a file from the location %s", s3Location));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(s3Location))
                .GET()
                .build();
        HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            File downloadFile = getNewTempFile();
            OutputStream outputStream = new FileOutputStream(downloadFile);
            try {
                response.body().transferTo(outputStream);
            } finally {
                outputStream.flush();
                outputStream.close();
            }
            logger.info(String.format("Storing file in folder %s", downloadFile.getAbsolutePath()));
            logger.info("Downloads import file from SFCC[Done]");
            return downloadFile;
        } else {
            throw new CatalogManagerException(String.format("[method downloadsInputFile] API Error StatusCode %d, Body : %s", response.statusCode(), response.body()));
        }

    }


    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    public List<File> ExtractImportFileFromZipFile(File downloadedFile) throws IOException {
        logger.info("Extracting import XML from compressed file");
        String fileZip = downloadedFile.getAbsolutePath();
        File destDir = new File(downloadedFile.getParent());
        List<File> results = new ArrayList<>();
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        try {
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    results.add(newFile);
                    logger.info(String.format("Extracted file %s", newFile.getAbsoluteFile()));
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }
        } finally {
            logger.info(String.format("Deleting file %s", downloadedFile.getAbsoluteFile()));
            downloadedFile.delete();
            logger.info(String.format("Deleted file %s", downloadedFile.getAbsoluteFile()));
            zis.closeEntry();
            zis.close();
        }
        return results;
    }


    public List<File> processXmlFiles(List<File> inputFiles) throws IOException, CatalogManagerException {
        logger.info("Processing import XML from compressed file");
        if (inputFiles.size() == 0) throw new CatalogManagerException("inputFiles is empty!!!");
        final File uploadFile = getNewTempFile();
        List<File> processedFilesList = new ArrayList<>();
        for (File input : inputFiles) {
            BufferedReader reader = new BufferedReader(new FileReader(input));
            try {

                File processedFile = getNewTempFile(FilenameUtils.getBaseName(input.getName())+"-"+OP_FILE_PREFIX, ".".concat(FilenameUtils.getExtension(input.getName())));
                BufferedWriter writer = new BufferedWriter(new FileWriter(processedFile));
                try {
                    String line = reader.readLine();
                    while (line != null) {
                        String wline = line.replaceAll("shared-variation-attribute", "variation-attribute");
                        writer.write(wline);
                        writer.write("\n");
                        line = reader.readLine();
                    }
                } finally {
                    writer.flush();
                    writer.close();
                }
                processedFilesList.add(processedFile);
                logger.info(String.format("Processed file is created in folder %s", processedFile.getAbsolutePath()));
            } finally {
                reader.close();
            }
        }
        return processedFilesList;
    }


    public void purgeFiles() throws IOException {
        FileUtils
                .listFiles(getNewTempFile().getParentFile(),  WildcardFileFilter.builder().setWildcards(String.format("*%s*", OP_FILE_PREFIX)).get(), null)
                .stream()
                .forEach(file -> {
                    logger.info(String.format("PURGING %s", file.getAbsolutePath()));
                    file.delete();
                });
    }


    public File compressProcessedFiles(List<File> processedFiles) throws IOException {
        File outputFile = getNewTempFile(OP_FILE_PREFIX, ".zip");
        final FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            ZipOutputStream zipOut = new ZipOutputStream(fos);
            try {
                for (File srcFile : processedFiles) {

                    FileInputStream fis = new FileInputStream(srcFile);
                    ZipEntry zipEntry = new ZipEntry(srcFile.getName());
                    zipOut.putNextEntry(zipEntry);
                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zipOut.write(bytes, 0, length);
                    }
                    fis.close();
                }
            } finally {
                zipOut.close();
            }
        }
        finally {
            fos.close();
        }
        logger.info(String.format("Catalog zip file has been created in %s", outputFile.getAbsolutePath()));
        return  outputFile;
    }

    public void uploadCatalogFileToSFCC (File catalogFile, OffsetDateTime refDateTime) throws IOException, InterruptedException, CatalogManagerException {
       //2023-08-16T11:38:54.419Z
        logger.info("Start uploading processed catalog to SFCC...");
        String postPayload = String.format("{\n" +
                "  \"job_id\": \"%s\",\n" +
                "  \"execution_id\": \"%s\",\n" +
                "  \"site_id\": \"%s\",\n" +
                "  \"cartridge_version\": \"1.0\"}",
                        refDateTime.toString(),
                refDateTime,
                        SITE_ID);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/sfcc-api/v1/job_config/catalog", this.host)))
                .setHeader("Authorization", String.format("Bearer %s", this.TOKEN))
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(postPayload))
                .build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            String uploadLink =gson.fromJson(response.body(), CatalogExportJobResponse.class).getUploadUrl();
            logger.info(String.format("Got new import task link : %s", uploadLink));

            HttpRequest uploadRequest = HttpRequest.newBuilder().
                    uri(URI.create(uploadLink))
                    //.header("Content-Type",  "application/zip")
                    //.header("Transfer-Encoding", "chunked")
                    .PUT(HttpRequest.BodyPublishers.ofFile(catalogFile.toPath()))
                    .build();
            try {

                HttpResponse<String> uploadResponse = this.httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
                if (uploadResponse.statusCode() == 200) {
                    logger.info(String.format("Got new import task link : %s", uploadResponse.body()));
                } else {
                    String error = String.format("Uploading presigned file, API Error StatusCode %d, Body : %s", uploadResponse.statusCode(), uploadLink);
                    logger.error(error);
                    throw new CatalogManagerException(error);
                }
            }
            catch (Exception e) {
                logger.error(e.toString());
                e.printStackTrace();
                throw e;
            }

        } else {
            String error = String.format("Starting, API Error StatusCode %d, Body : %s", response.statusCode(), response.body());
            logger.error(error);
            throw new CatalogManagerException(error);
        }

    }

    public File getNewTempFile() throws IOException {
        return this.getNewTempFile(OP_FILE_PREFIX, ".zip");
    }

    public File getNewTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix);
    }



    public String getS3FileFromNewstore(String importFileUrl) throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(importFileUrl))
                .setHeader("Authorization", String.format("Bearer %s", this.TOKEN))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 302) {
            String url = response.body();
            int index = url.indexOf("http");
            return url.substring(index);
        } else {
            throw new IllegalStateException("Server does not provide back redirection.");
        }
    }

}


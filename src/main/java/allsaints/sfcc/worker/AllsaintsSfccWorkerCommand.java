package allsaints.sfcc.worker;

import allsaints.sfcc.worker.model.JobsItem;
import allsaints.sfcc.worker.model.NwsSFCCJobHistory;

import io.micronaut.configuration.picocli.PicocliRunner;


import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Command(name = "allsaints-sfcc-worker",
        description = "allsaints-sfcc-worker",
        mixinStandardHelpOptions = true
        )
public class AllsaintsSfccWorkerCommand implements Runnable {

    @Option(names = {"-u", "--user"}, description = "...", defaultValue = "")
    String userName;

    @Option(names = {"-p", "--password"}, description = "...", defaultValue = "")
    String password;

    @Option(names = {"-t", "--tenant"}, description = "...", defaultValue = "")
    String tenant;

    @Option(names = {"-fs", "--forcestart"}, description = "...", defaultValue = "false")
    boolean forceFirstTimeRun;



    public static void main(String[] args) throws Exception {
        PicocliRunner.run(AllsaintsSfccWorkerCommand.class, args);
    }

    public void run() {
        int iExitCode = 0;
        // business logic here
        try {
            CatalogManagerConfig config = new CatalogManagerConfig(
                    this.userName,
                    this.password,
                    this.tenant,
                    this.forceFirstTimeRun);
            CatalogManager catalogManager = CatalogManager.create(config);
            catalogManager.obtainToken();
            catalogManager.purgeFiles();
            Optional<NwsSFCCJobHistory> imports = catalogManager.readImports();
            if (imports.isPresent()) {
                Optional<JobsItem> jobsItem = catalogManager.getImportTaskCandidate(imports.get());
                if (jobsItem.isPresent()) {
                    OffsetDateTime refDatetime = OffsetDateTime.parse(jobsItem.get().getCreatedAt());
                    File downloadedFile = catalogManager.downloadsInputFile(jobsItem.get());
                    List<File> extractedFiles = catalogManager.ExtractImportFileFromZipFile(downloadedFile);
                    List<File> processedFiles = catalogManager.processXmlFiles(extractedFiles);
                    File fileToUpload = catalogManager.compressProcessedFiles(processedFiles);
                    catalogManager.uploadCatalogFileToSFCC(fileToUpload, refDatetime);
                    catalogManager.purgeFiles();
                } else {
                    LoggerFactory.getLogger(CatalogManager.class).info("No active import to fix, exiting...");
                }
            } else
            {
                LoggerFactory.getLogger(CatalogManager.class).info("No import is found, exiting...");
            }

            System.exit(iExitCode);
        } catch (IOException e) {
            iExitCode = 99;
            e.printStackTrace();
        } catch (InterruptedException e) {
            iExitCode = 98;
            e.printStackTrace();
        } catch (CatalogManagerException e) {
            iExitCode = 97;
            e.printStackTrace();
        }
        System.exit(iExitCode);
    }
}

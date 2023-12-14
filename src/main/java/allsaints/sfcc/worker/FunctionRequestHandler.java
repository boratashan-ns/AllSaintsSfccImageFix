package allsaints.sfcc.worker;

import allsaints.sfcc.worker.model.JobsItem;
import allsaints.sfcc.worker.model.NwsSFCCJobHistory;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.micronaut.function.aws.MicronautRequestHandler;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Inject;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

public class FunctionRequestHandler extends MicronautRequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent execute(APIGatewayProxyRequestEvent input) {

        String path = input.getPath();
        LoggerFactory.getLogger(CatalogManager.class).info("Starting...");


        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        String userName = System.getenv("USERNAME");
        String password = System.getenv("PASSWORD");
        String tenant = System.getenv("TENANT");
        boolean forceFirstTime = System.getenv("FORCE_FIRST_USAGE").equalsIgnoreCase("Y");

        CatalogManagerConfig config = new CatalogManagerConfig(userName, password, tenant, forceFirstTime);
        LoggerFactory.getLogger(CatalogManager.class).info(String.format("Configuration : %s", config.toString()));
        CatalogManager catalogManager = CatalogManager.create(config);
        try {
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
            } else {
                LoggerFactory.getLogger(CatalogManager.class).info("No import is found, exiting...");
            }
            response.setStatusCode(200);
        } catch (IOException e) {
            response.setStatusCode(500);
            LoggerFactory.getLogger(CatalogManager.class).info(e.getMessage());
        } catch (InterruptedException e) {
            response.setStatusCode(500);
            LoggerFactory.getLogger(CatalogManager.class).info(e.getMessage());
        } catch (CatalogManagerException e) {
            response.setStatusCode(500);
            LoggerFactory.getLogger(CatalogManager.class).info(e.getMessage());
        }
        return response;
    }


}

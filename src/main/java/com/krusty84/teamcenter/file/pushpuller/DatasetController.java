package com.krusty84.teamcenter.file.pushpuller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamcenter.clientx.AppXSession;
import com.teamcenter.services.loose.core._2006_03.FileManagement;
import com.teamcenter.services.strong.core.DataManagementService;
import com.teamcenter.services.strong.core.SessionService;
import com.teamcenter.services.strong.core._2007_01.Session;
import com.teamcenter.services.strong.core._2010_04.DataManagement;
import com.teamcenter.soa.client.Connection;
import com.teamcenter.soa.client.FileManagementUtility;
import com.teamcenter.services.strong.core.FileManagementService;
import com.teamcenter.soa.client.model.strong.ImanFile;
import com.teamcenter.soa.common.ObjectPropertyPolicy;
import com.teamcenter.soa.common.PolicyProperty;
import com.teamcenter.soa.common.PolicyType;
import com.teamcenter.soa.client.model.ServiceData;
import com.teamcenter.soa.client.model.strong.Dataset;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

@RestController
public class DatasetController {

    private final Config appConfig;

    public DatasetController(Config appConfig) {
        this.appConfig = appConfig;
    }
//region End-Points
    @PostMapping("/create-dataset")
    public ResponseEntity<String> createDataset(
            @RequestParam String dsName,
            @RequestParam String dsDesc,
            @RequestParam String dsType,
            @RequestParam String dsNamedReferencedName,
            @RequestParam("fileNameToUpload") MultipartFile file) {

        String tcServerUrl = appConfig.getTcServerUrl();
        String tcUserName = appConfig.getTcUserName();
        String tcUserPassword = appConfig.getTcUserPassword();
        String fileCacheDir = appConfig.getFileCacheDir();

        AppXSession currentSession = null;
        try {
            currentSession = new AppXSession(tcServerUrl);
            currentSession.login(tcUserName, tcUserPassword);
            DataManagementService dsService = DataManagementService.getService(currentSession.getConnection());
            FileManagementUtility fileUtility = prepareFileUtility(currentSession, fileCacheDir);
            String result = createDataset(dsName, dsDesc, dsType, dsNamedReferencedName, file, dsService, fileUtility);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } finally {
            if (currentSession != null) {
                try {
                    currentSession.logout();
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }
    }

    @PostMapping("/download-dataset")
    public ResponseEntity<?> downloadDataset(@RequestBody Map<String, String> body) {
        String dsUid = body.get("dsUid");
        String tcServerUrl = appConfig.getTcServerUrl();
        String tcUserName = appConfig.getTcUserName();
        String tcUserPassword = appConfig.getTcUserPassword();
        String fileCacheDir = appConfig.getFileCacheDir();

        AppXSession currentSession = null;
        try {
            currentSession = new AppXSession(tcServerUrl);
            currentSession.login(tcUserName, tcUserPassword);
            DataManagementService dsService = DataManagementService.getService(currentSession.getConnection());
            return downloadDataset(dsUid, dsService, currentSession.getConnection());

        }catch (FileNotFoundException e) {
            // Return 404 if the file was not found at the URL
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found: " + e.getMessage());
        } catch (IOException e) {
            // Return 500 for IO issues during file download
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while downloading the file: " + e.getMessage());
        }
        catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        } finally {
            if (currentSession != null) {
                try {
                    currentSession.logout();
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }
    }
//endregion

//region Calling Teamcenter
    private String createDataset(String dataSetName, String datasetDesc, String datasetType, String datasetNamedReferencedName,
                                 MultipartFile multipartFile, DataManagementService dsService, FileManagementUtility fileUtility) throws Exception {
        File tempFile = File.createTempFile("upl-", multipartFile.getOriginalFilename());
        multipartFile.transferTo(tempFile);
        try {
            DataManagement.DatasetInfo[] dsInfos = new DataManagement.DatasetInfo[]{new DataManagement.DatasetInfo()};
            dsInfos[0].clientId = "";
            dsInfos[0].name = dataSetName;
            dsInfos[0].description = datasetDesc;
            dsInfos[0].type = datasetType;
            dsInfos[0].toolUsed = "";

            DataManagement.CreateDatasetsResponse dsResp = dsService.createDatasets(dsInfos);
            Dataset dataset = dsResp.datasetOutput[0].dataset;

            FileManagement.DatasetFileInfo dsFileInfo = new FileManagement.DatasetFileInfo();
            dsFileInfo.clientId = "";
            dsFileInfo.fileName = tempFile.getAbsolutePath();
            dsFileInfo.namedReferencedName = datasetNamedReferencedName;
            dsFileInfo.isText = false;
            dsFileInfo.allowReplace = false;

            FileManagement.DatasetFileInfo[] dsFileInfos = new FileManagement.DatasetFileInfo[1];
            dsFileInfos[0] = dsFileInfo;

            FileManagement.GetDatasetWriteTicketsInputData inputData = new FileManagement.GetDatasetWriteTicketsInputData();
            inputData.dataset = dataset;
            inputData.createNewVersion = true;
            inputData.datasetFileInfos = dsFileInfos;

            FileManagement.GetDatasetWriteTicketsInputData[] dsWriteTicket = new FileManagement.GetDatasetWriteTicketsInputData[]{inputData};

            ServiceData serviceData = fileUtility.putFiles(dsWriteTicket);
            checkTCServiceError(serviceData);

            return getJsonResponse(dataset.getUid().toString(), datasetType,dataSetName,datasetDesc);
        } finally {
            // Delete the temporary file
            tempFile.delete();
        }
    }

    private ResponseEntity<byte[]> downloadDataset(String dsUid, DataManagementService dsService, Connection connection) throws Exception {
        URLConnection conn = null;
        String fileName = null;
        List<URL> directDownloadFileUrl = new ArrayList<URL>();
        SessionService currentSession =  SessionService.getService(connection);
        Session.ScopedPreferenceNames[] arrayOfScopedPreferenceNames = new Session.ScopedPreferenceNames[1];
        arrayOfScopedPreferenceNames[0] = new Session.ScopedPreferenceNames();
        arrayOfScopedPreferenceNames[0].names = new String[]{"Fms_BootStrap_Urls"};
        arrayOfScopedPreferenceNames[0].scope = "Site";
        Session.MultiPreferencesResponse localMultiPreferencesResponse = currentSession.getPreferences(arrayOfScopedPreferenceNames);
        String[] bootStrapURL = localMultiPreferencesResponse.preferences[0].values;
        System.out.println(bootStrapURL[0]);
        ObjectPropertyPolicy propertyPolicySOA = new ObjectPropertyPolicy();
        propertyPolicySOA.addType(new PolicyType("ImanFile", new String[] { "original_file_name" }));
        propertyPolicySOA.addType(new PolicyType("Dataset", new String[] { "ref_list" }, new String[] { PolicyProperty.WITH_PROPERTIES }));
        currentSession.setObjectPropertyPolicy(propertyPolicySOA);

        ServiceData serviceData = dsService.loadObjects(new String[] { dsUid });

        if (serviceData.sizeOfPlainObjects() == 0)
            throw new Exception("Cannot find dataset.");

        Dataset targetDataset = (Dataset) serviceData.getPlainObject(0);
        ImanFile[] namedFileReferences = Arrays.copyOf(targetDataset.get_ref_list(), targetDataset.get_ref_list().length, ImanFile[].class);
        FileManagementService fileMgtService = FileManagementService.getService(connection);
        com.teamcenter.services.strong.core._2006_03.FileManagement.FileTicketsResponse fileTickets = fileMgtService.getFileReadTickets(namedFileReferences);
        for (ImanFile imanFile : namedFileReferences) {
            //System.out.println("File Name from Dataset Ref: "+imanFile.get_original_file_name());
            directDownloadFileUrl.add(new URL(String.format("%s//%s?ticket=%s",
                    "http://192.168.1.33:4544",
                    imanFile.get_original_file_name(),
                    (String)fileTickets.tickets.get(imanFile))));
        }

        for (URL url : directDownloadFileUrl) {
            //System.out.println("Direct File Url: "+url);
            fileName=extractFileName(url);
            conn = url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.connect();
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = conn.getInputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/octet-stream"));
        headers.setContentDispositionFormData("attachment", fileName);

        return new ResponseEntity<>(byteArrayOutputStream.toByteArray(), headers, HttpStatus.OK);
    }
//endregion

//region Helpers
    private FileManagementUtility prepareFileUtility(AppXSession session, String FileCacheDir) throws Exception {
    SessionService currentSession = SessionService.getService(session.getConnection());
    Session.ScopedPreferenceNames[] arrayOfScopedPreferenceNames = new Session.ScopedPreferenceNames[1];
    arrayOfScopedPreferenceNames[0] = new Session.ScopedPreferenceNames();
    arrayOfScopedPreferenceNames[0].names = new String[]{"Fms_BootStrap_Urls"};
    arrayOfScopedPreferenceNames[0].scope = "Site";
    Session.MultiPreferencesResponse localMultiPreferencesResponse = currentSession.getPreferences(arrayOfScopedPreferenceNames);
    String[] bootStrapURL = localMultiPreferencesResponse.preferences[0].values;
    return new FileManagementUtility(session.getConnection(), null, null, bootStrapURL, FileCacheDir);
}
    private void checkTCServiceError(ServiceData data) throws Exception {
        if (data.sizeOfPartialErrors() > 0) {
            StringBuilder errorMsg = new StringBuilder();
            for (int i = 0; i < data.sizeOfPartialErrors(); i++) {
                for (String msg : data.getPartialError(i).getMessages()) {
                    errorMsg.append(msg).append("\n");
                }
            }
            throw new Exception(errorMsg.toString());
        }
    }
    private String extractFileName(URL url) {
        String[] pathParts = url.getPath().split("/");
        return pathParts[pathParts.length - 1];
    }
    private String getJsonResponse(String uid, String type, String name, String desc) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> response = new HashMap<>();
            response.put("uid", uid);
            response.put("type", type);
            response.put("name", name);
            response.put("description", desc);
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
//endregion
}

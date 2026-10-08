package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String uploadFile(MultipartFile file) throws Exception;
    void deleteFile(String objectName) throws Exception;
}

package com.krusty84.teamcenter.file.pushpuller;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class Config {
    private String tcServerUrl;
    private String tcUserName;
    private String tcUserPassword;
    private String fileCacheDir;

    // Getters and Setters
    public String getTcServerUrl() {
        return tcServerUrl;
    }

    public void setTcServerUrl(String tcServerUrl) {
        this.tcServerUrl = tcServerUrl;
    }

    public String getTcUserName() {
        return tcUserName;
    }

    public void setTcUserName(String tcUserName) {
        this.tcUserName = tcUserName;
    }

    public String getTcUserPassword() {
        return tcUserPassword;
    }

    public void setTcUserPassword(String tcUserPassword) {
        this.tcUserPassword = tcUserPassword;
    }

    public String getFileCacheDir() {
        return fileCacheDir;
    }

    public void setFileCacheDir(String fileCacheDir) {
        this.fileCacheDir = fileCacheDir;
    }
}

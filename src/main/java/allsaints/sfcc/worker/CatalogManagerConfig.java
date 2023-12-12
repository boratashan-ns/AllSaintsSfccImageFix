package allsaints.sfcc.worker;

public class CatalogManagerConfig {
    private final String userName;
    private final String password;
    private final String tenant;
    private final boolean forceFirstTimeRun;

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getTenant() {
        return tenant;
    }

    public boolean isForceFirstTimeRun() {
        return forceFirstTimeRun;
    }

    public CatalogManagerConfig(String userName, String password, String tenant, boolean forceFirstTimeRun) {
        this.userName = userName;
        this.password = password;
        this.tenant = tenant;
        this.forceFirstTimeRun = forceFirstTimeRun;
    }

    @Override
    public String toString() {
        return "CatalogManagerConfig{" +
                "userName='" + userName + '\'' +
                ", password='" + "********"+ '\'' +
                ", tenant='" + tenant + '\'' +
                ", forceFirstTimeRun=" + forceFirstTimeRun +
                '}';
    }
}

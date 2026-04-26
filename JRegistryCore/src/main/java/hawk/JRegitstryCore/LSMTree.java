package hawk.JRegitstryCore;

import hawk.JRegitstryCore.Log.LogEntry;

public interface LSMTree {

    public boolean put(String key, byte[] value, String type);
    public String get(String key);
    public boolean delete(String key);
    public boolean persist();

    public void applyLog(LogEntry logEntry);

}
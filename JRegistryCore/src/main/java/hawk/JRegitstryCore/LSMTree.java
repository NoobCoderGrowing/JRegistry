package hawk.JRegitstryCore;

import hawk.JRegitstryCore.Log.LogEntry;
import java.util.concurrent.ThreadPoolExecutor;

public interface LSMTree {

    public boolean put(String key, byte[] value, String type);
    public Pair<String, byte[]> get(String key);
    public boolean delete(String key);
    public boolean persist(ThreadPoolExecutor writePool);

    public boolean applyLog(LogEntry logEntry);

}
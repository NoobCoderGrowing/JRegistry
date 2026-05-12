package hawk.JRegitstryCore;

import hawk.JRegitstryCore.Log.LogEntry;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.Set;

public interface LSMTree {

    public boolean put(String key, byte[] value, String type);
    public Pair<String, byte[]> get(String key);
    public boolean delete(String key);
    public boolean persist(ThreadPoolExecutor writePool);

    public boolean applyLog(LogEntry logEntry);

    public BPlusNode cd(String path);

    // public String pwd();

    // public Set<String> ls();

    // public String show();

    // public boolean restore();

    public BPlusNode getRoot();

}
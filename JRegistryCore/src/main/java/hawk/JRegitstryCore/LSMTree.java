// package hawk.JRegitstryCore;

// import hawk.JRegitstryCore.Log.LogEntry;
// import java.util.concurrent.ThreadPoolExecutor;
// import com.alibaba.fastjson.annotation.JSONType;

// @JSONType(seeAlso = {BPlusTree.class})
// public interface LSMTree {

//     public boolean put(LogEntry logEntry);
//     public Pair<String, byte[]> get(String key);
//     public boolean delete(String key);
//     public boolean persist(ThreadPoolExecutor writePool);

//     public boolean applyLog(LogEntry logEntry);

//     public BPlusNode cd(String path, BPlusNode position);

//     // public String pwd();

//     // public Set<String> ls();

//     // public String show();

//     // public boolean restore();

//     public BPlusNode getRoot();

//     public void rebuildParentLinks();

// }
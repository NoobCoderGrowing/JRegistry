package hawk.JRegistryCenter.Raft.Log;

import org.springframework.beans.factory.annotation.Value;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import hawk.JRegitstryCore.Log.LogEntry;


public class LogCommitServiceWatcher{

    @Value("${raft.count}")
    private int nodeCount;

    private TreeMap<LogEntry, Integer> commitMap;
    private AtomicBoolean mapCasLock;
    private LogService logService;

    @Value("${raft.commitMapLimit}")
    private int commitMapLimit;


    public LogCommitServiceWatcher(TreeMap<LogEntry, Integer> commitMap, AtomicBoolean mapCasLock, LogService logService){
        this.commitMap = commitMap;
        this.mapCasLock = mapCasLock;
        this.logService = logService;
    }
    
    public void update(){
        while(!mapCasLock.compareAndSet(false, true)){}
        while(commitMap.size()>0&&commitMap.firstEntry().getValue()>(nodeCount/2)){
            LogEntry logEntry = commitMap.firstKey();
            logService.commitLog(logEntry.getTerm(), logEntry.getIndex());
            commitMap.put(logEntry, 0);
        }
        if(commitMap.size() > commitMapLimit){
            clearHalfCommitMap();
        }

        mapCasLock.set(false);
    }

    public void clearHalfCommitMap(){
        while(commitMap.size()>commitMapLimit/2){
            commitMap.remove(commitMap.firstKey());
        }
    }
}

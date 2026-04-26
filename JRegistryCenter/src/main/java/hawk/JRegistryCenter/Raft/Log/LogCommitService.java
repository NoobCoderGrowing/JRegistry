package hawk.JRegistryCenter.Raft.Log;

import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import hawk.JRegitstryCore.RPC.RaftRequest;
import hawk.JRegitstryCore.Log.LogEntry;




@Service
public class LogCommitService {
     
    public TreeMap<LogEntry, Integer> commitMap = new TreeMap<>();
    public AtomicBoolean mapCasLock = new AtomicBoolean(false);
    @Autowired
    private LogService logService;
    private LogCommitServiceWatcher watcher;   
    
    @PostConstruct
    public void registerWatcher(){
        this.watcher = new LogCommitServiceWatcher(commitMap, mapCasLock, logService);
    }

    public void unregisterWatcher(LogCommitServiceWatcher watcher){
        this.watcher = null;
    }

    public void notifyWatcher(){
        watcher.update();
    }

    public void updateCommitMap(RaftRequest reply){
        while(!mapCasLock.compareAndSet(false, true)){}
        commitMap.put(reply.getLog(), commitMap.getOrDefault(reply.getLog(), 0) + 1);
        mapCasLock.set(false);
        notifyWatcher();
    }
}

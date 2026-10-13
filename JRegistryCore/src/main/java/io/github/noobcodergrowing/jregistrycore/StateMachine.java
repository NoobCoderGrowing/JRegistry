package io.github.noobcodergrowing.jregistrycore;

import io.github.noobcodergrowing.jregistrycore.Log.LogEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import com.alibaba.fastjson.JSON;
import java.io.FileOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.io.BufferedWriter;
import javax.annotation.PostConstruct;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.noobcodergrowing.jregistrycore.RPC.RaftRequest;


@Slf4j
@Data
public class StateMachine {

    private BPlusNode root;

    private volatile long commitIndex;

    @JSONField(serialize = false)
    private volatile long leaderCommit;

    @Value("${raft.image-path}")
    private String imagePath;


    @Value("${raft.node-id}")
    @JSONField(serialize = false)
    private int id;

    @Autowired
    @JSONField(serialize = false)
    private ThreadPoolExecutor persistThread;

    @JSONField(serialize = false)
    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    @JSONField(serialize = false)
    private volatile Path SMFilePath;

    @JSONField(serialize = false)
    private volatile BufferedWriter SMWriter;

    /**
     * 归属表：规范化 key -> 写入它的 clientId。只记录带 clientId 的写入（JRegistryClient 的 set），
     * SSH / Admin 等无 clientId 的写入不进表，因此不受租约清理影响。
     * 随 StateMachine 一起序列化，用于快照安装与本地恢复后重建。
     */
    private Map<String, String> ownership = new ConcurrentHashMap<>();

    /** 租约表：clientId -> 当前归属（owner 节点 + epoch 任期）。 */
    private Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public StateMachine() {
        this.root = new BPlusNode("root", "/root" );
        this.commitIndex = -1;
        this.leaderCommit = -1;
    }

    @PostConstruct
    public void initLogWriter(){
        SMFilePath = Path.of(imagePath+"stateMachine"+ id +".json");
        openLogWriter();
    }

    public boolean putIfAbsent(String key, byte[] value, String type){
        String[] paths = KeyPath.split(key);
        BPlusNode current = root;
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){
                current = current.getChildren().get(paths[i]);
            }else{ // if the node is not exists, add it
                BPlusNode newNode = new BPlusNode(paths[i]);
                current.addNode(newNode);
                current = newNode;
            }
        }
        if(current.getChildren().containsKey(paths[paths.length-1])){// if the node is already exists, return false
            return false;
        }else{// if the node is not exists, add it
            BPlusNode newNode = new BPlusNode(paths[paths.length-1], value, type);
            current.addNode(newNode);
            newNode.setValue(value);
            newNode.setType(type);
            return true;
        }
    }

    public boolean put(LogEntry logEntry){
        String key = logEntry.getKey();
        byte[] value = logEntry.getData();
        String type = logEntry.getDataType();

        String[] paths = KeyPath.split(key);
        BPlusNode current = root;
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){
                current = current.getChildren().get(paths[i]);
            }else{ // if the node is not exists, add it
                BPlusNode newNode = new BPlusNode(paths[i]);
                current.addNode(newNode);
                current = newNode;
            }
        }

        BPlusNode newNode = new BPlusNode(paths[paths.length-1], value, type);
        current.addNode(newNode);
        log.info("current node info: {}", current.show());
        if(logEntry.getClientId() != null){
            String normalized = KeyPath.normalize(key);
            String previousOwner = ownership.put(normalized, logEntry.getClientId());
            // 归属转移：旧 owner 若已离线、且因此不再拥有任何 key，顺手回收那行空壳簿记。
            if(previousOwner != null && !previousOwner.equals(logEntry.getClientId())){
                collectOrphanSessionIfAny(previousOwner);
            }
        }
        return true;
    }


    public Pair<String, byte[]> get(String key){
        String[] paths = KeyPath.split(key);
        BPlusNode current = root;
        for (int i = 0; i < paths.length; i++) {
            if(current.getChildren().containsKey(paths[i])){
                current = current.getChildren().get(paths[i]);
            }else{
                return null;
            }
        }
        return new Pair<String, byte[]>(current.getType(), current.getValue());
    }

    /**
     * 递归列出 {@code path} 节点及其所有后代中带值的节点，返回的 key 是绝对完整路径
     * （形如 {@code app/config/name}，即 {@link #get} 接受的形式，不含 {@code root} 前缀）。
     * {@code path} 本身不存在时返回 {@code null}；存在但没有带值的后代时返回空列表。
     */
    public List<KV> ls(String path){
        String[] paths = KeyPath.split(path);
        BPlusNode current = root;
        for (int i = 0; i < paths.length; i++) {
            if(current.getChildren() == null || !current.getChildren().containsKey(paths[i])){
                return null;
            }
            current = current.getChildren().get(paths[i]);
        }
        List<KV> result = new ArrayList<>();
        // 遍历结束后 current 是「查询路径最后一段」那个节点，它之上的段要当作前缀补回去。
        // 传空串会让深层查询丢掉最外层：ls("cloud/text-indexer") 会算出 "text-indexer/..."，
        // 既不再是注释承诺的绝对路径，也让 isKeyOffline 拿这个缺前缀的 key 去归属表里查空。
        // paths.length == 1 时前缀为空串，浅层查询行为不变。
        collectKVs(current, String.join(KeyPath.SEPARATOR, Arrays.copyOf(paths, paths.length - 1)), result);
        return result;
    }

    /** 先序收集带值的节点：{@code prefix} 是父节点累积出的绝对路径，root 层为空串。 */
    private void collectKVs(BPlusNode node, String prefix, List<KV> result){
        String path = prefix.isEmpty() ? node.getKey() : prefix + KeyPath.SEPARATOR + node.getKey();
        if(node.getValue() != null){
            result.add(new KV(path, node.getValue(), node.getType(), isKeyOffline(path)));
        }
        if(node.getChildren() == null){
            return;
        }
        for(BPlusNode child : node.getChildren().values()){
            collectKVs(child, path, result);
        }
    }

    public RaftRequest handleLsRequest(RaftRequest request){
        List<KV> kvs = ls(request.getKey());
        RaftRequest reply = new RaftRequest();
        reply.setType("ls");
        reply.setKey(request.getKey());
        reply.setUuid(request.getUuid());
        if(kvs == null){
            reply.setSuccess(false);
            return reply;
        }
        reply.setSuccess(true);
        reply.setKvs(kvs);
        return reply;
    }

    public RaftRequest handleGetRequest(RaftRequest request){
        Pair<String, byte[]> result = get(request.getKey());
        RaftRequest reply = new RaftRequest();
        reply.setType("get");
        reply.setKey(request.getKey());
        reply.setUuid(request.getUuid());
        if(result != null){
            reply.setSuccess(true);
            reply.setData(result.getRight());
            reply.setDataType(result.getLeft());
            reply.setOffline(isKeyOffline(KeyPath.normalize(request.getKey())));
            return reply;
        }else{

            reply.setSuccess(false);
            return reply;
        }
    }

    public boolean delete(String key){
        String[] paths = KeyPath.split(key);
        String previousOwner = ownership.remove(KeyPath.normalize(key));
        if(previousOwner != null){
            collectOrphanSessionIfAny(previousOwner);
        }
        BPlusNode current = root;
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){
                current = current.getChildren().get(paths[i]);
            }else{
                return false;
            }
        }
        if(current.getChildren().containsKey(paths[paths.length-1])){
            current.deleteNode(paths[paths.length-1]);
            pruneEmptyAncestors(current);
            return true;
        }else{
            return false;
        }
    }

    /**
     * 删除叶子后，把因此变空、且自身没有值的祖先节点一并摘掉（unix rmdir 语义）。
     * 同时有值和子节点的中转节点不受影响：只有「既无值又无子节点」才会被清理。
     */
    private void pruneEmptyAncestors(BPlusNode node){
        while(node != null && node != root
                && node.getValue() == null
                && (node.getChildren() == null || node.getChildren().isEmpty())){
            BPlusNode parent = node.getParent();
            if(parent == null){
                return;
            }
            parent.getChildren().remove(node.getKey());
            node = parent;
        }
    }

    /**
     * 登记一次 client 归属：把 clientId 判给 {@code ownerNodeId}，并把 epoch 自增一任。
     *
     * <p>epoch 的自增在 applyLog 里做（而不是在提交日志的节点上做），因此每个副本对同一份日志
     * 得到完全相同的任期号——这是守卫可重放的前提。
     */
    private boolean clientAttach(LogEntry logEntry){
        String clientId = logEntry.getClientId();
        if(clientId == null || clientId.isEmpty()){
            log.warn("node {} skip clientAttach with empty clientId, index {}", id, logEntry.getIndex());
            return false;
        }
        SessionInfo session = sessions.computeIfAbsent(clientId, k -> new SessionInfo());
        session.setOwnerNodeId(logEntry.getOwnerNodeId());
        session.setEpoch(session.getEpoch() + 1);
        session.setOffline(false);
        session.setOfflineSinceMillis(0L); // 重新上线，离线计时清零
        log.info("node {} client {} attached to node {}, epoch {}, now online",
                id, clientId, session.getOwnerNodeId(), session.getEpoch());
        return true;
    }

    /**
     * 租约过期的判决：把该客户端标成<b>离线</b>。<b>只翻状态，不删任何 key。</b>
     * 守卫在这里：只有当这条判决针对的仍然是当前这一次归属时才执行，否则当作陈旧判决丢弃。
     *
     * <p>为什么守卫必须存在：离线判决是在检测节点上、根据它当时已经 apply 的状态做出的，
     * 而真正执行要等日志走到这一步。这中间客户端可能已经带着同一个 clientId 重连（甚至重连回
     * 同一个节点），归属早已换了一任。被拒绝不代表判决丢失——归属已经转移给了别的节点，
     * 由那一任 owner 负责它后续的死活。
     *
     * <p>为什么只比 owner 不够：owner 会重复。同一个节点可以先后两次成为 owner（客户端重连回同一节点、
     * 客户端重启后连回同一节点、节点自身重启后再接管），只比 owner 就会把「上一任的判决」误用到
     * 「这一任的数据」上。epoch 是唯一能区分两任归属的字段。
     */
    private boolean clientOffline(LogEntry logEntry){
        String clientId = logEntry.getClientId();
        if(clientId == null || clientId.isEmpty()){
            return false;
        }
        SessionInfo session = sessions.get(clientId);
        if(session == null){
            log.warn("node {} ignore clientOffline for unknown client {}", id, clientId);
            return false;
        }
        if(session.getEpoch() != logEntry.getClientEpoch()
                || session.getOwnerNodeId() != logEntry.getOwnerNodeId()){
            log.warn("node {} ignore stale clientOffline {}: entry(epoch={}, owner={}) current(epoch={}, owner={})",
                    id, clientId, logEntry.getClientEpoch(), logEntry.getOwnerNodeId(),
                    session.getEpoch(), session.getOwnerNodeId());
            return false;
        }

        session.setOffline(true);
        // 离线时刻由 leader 在追加日志时写进 LogEntry，这里只负责存下来——重放才能得到同一个值。
        session.setOfflineSinceMillis(logEntry.getOfflineSinceMillis());
        log.info("node {} mark client {} offline (owner {} epoch {}), it still holds {} key(s)",
                id, clientId, session.getOwnerNodeId(), session.getEpoch(), keysOwnedBy(clientId).size());
        // 离线且不再拥有任何 key 的空壳簿记就地回收；一条 key 数据都不动。
        collectOrphanSessionIfAny(clientId);
        return true;
    }

    /**
     * 离线数据 TTL 回收的判决：删掉该客户端离线期间留下的所有 key，并连同会话记录一起移除。
     *
     * <p>守卫与 {@link #clientOffline} 同形，<b>只做确定性判断、不比较时间</b>：TTL 到没到是 leader
     * 提议时按本地时钟判定的，apply 阶段只确认「这条判决针对的那一任归属仍然处于离线」。
     * 客户端若在这期间重新上线过（epoch 涨了）、或者归属已经换了节点，判决就作废——
     * 免得把刚续上的数据误删。
     */
    private boolean reapOffline(LogEntry logEntry){
        String clientId = logEntry.getClientId();
        if(clientId == null || clientId.isEmpty()){
            return false;
        }
        SessionInfo session = sessions.get(clientId);
        if(session == null){
            log.warn("node {} ignore reapOffline for unknown client {}", id, clientId);
            return false;
        }
        if(!session.isOffline()
                || session.getEpoch() != logEntry.getClientEpoch()
                || session.getOwnerNodeId() != logEntry.getOwnerNodeId()){
            log.warn("node {} ignore stale reapOffline {}: entry(epoch={}, owner={}) current(epoch={}, owner={}, offline={})",
                    id, clientId, logEntry.getClientEpoch(), logEntry.getOwnerNodeId(),
                    session.getEpoch(), session.getOwnerNodeId(), session.isOffline());
            return false;
        }

        Set<String> keys = keysOwnedBy(clientId);
        for(String key : keys){
            delete(key);
        }
        sessions.remove(clientId);
        log.info("node {} reclaimed {} expired offline key(s) of client {} (offline since {})",
                id, keys.size(), clientId, session.getOfflineSinceMillis());
        return true;
    }

    /**
     * 供 leader 侧回收扫描使用：仍处于离线、且离线时刻早于 {@code cutoffMillis} 的全部 clientId。
     *
     * <p>纯查询，不做任何修改——真正的删除仍要走 {@code reapOffline} 日志才能全集群收敛。
     * {@code offlineSinceMillis == 0} 的会话（本特性上线前就已离线的那批）不在此列：
     * 没有可信的起点就不动它，免得升级瞬间误删一批老数据。
     */
    public Set<String> sessionsOfflineBefore(long cutoffMillis){
        Set<String> expired = ConcurrentHashMap.newKeySet();
        for(Map.Entry<String, SessionInfo> entry : sessions.entrySet()){
            SessionInfo session = entry.getValue();
            if(session.isOffline()
                    && session.getOfflineSinceMillis() > 0
                    && session.getOfflineSinceMillis() < cutoffMillis){
                expired.add(entry.getKey());
            }
        }
        return expired;
    }

    /**
     * 已离线、且不再拥有任何 key 的会话记录就地回收。<b>只删簿记，不涉及任何 key 数据。</b>
     *
     * <p>在线的会话绝不回收——它可能只是暂时还没 set 东西。回收是安全的：一行不指向任何数据的记录
     * 没有任何东西会引用它；客户端将来若用同一个 clientId 重连，{@code clientAttach} 会重建这一行，
     * 而守卫里「会话不存在 → 忽略」这条本来就兜得住陈旧的离线判决。
     */
    private void collectOrphanSessionIfAny(String clientId){
        SessionInfo session = sessions.get(clientId);
        if(session == null || !session.isOffline()){
            return;
        }
        if(!keysOwnedBy(clientId).isEmpty()){
            return;
        }
        sessions.remove(clientId);
        log.info("node {} collect empty offline session {}", id, clientId);
    }

    /** 该 key 所属客户端是否离线。无归属（SSH / Admin 写入）视为生效。 */
    private boolean isKeyOffline(String path){
        String clientId = ownership.get(path);
        if(clientId == null){
            return false;
        }
        SessionInfo session = sessions.get(clientId);
        return session != null && session.isOffline();
    }

    /** 当前属于 {@code clientId} 的 key（规范化路径）。仅用于本地预检与观测。 */
    public Set<String> keysOwnedBy(String clientId){
        Set<String> keys = ConcurrentHashMap.newKeySet();
        if(clientId == null){
            return keys;
        }
        for(Map.Entry<String, String> entry : ownership.entrySet()){
            if(clientId.equals(entry.getValue())){
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /** 当前租约；未知 clientId 返回 {@code null}。 */
    public SessionInfo getSession(String clientId){
        return clientId == null ? null : sessions.get(clientId);
    }

    public boolean persist(ThreadPoolExecutor writePool){
        String serializedTree = JSON.toJSONString(this);
        writePool.execute(() -> {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream("lsmTree.json");
                fileOutputStream.write(serializedTree.getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return true;
    }

    public boolean applyLog(LogEntry logEntry){
        String cmd = logEntry.getCommand();
        log.info("apply log: {}", JSON.toJSONString(logEntry));
        boolean success = false;
        // A malformed key must never propagate: the caller advances commitIndex only after this
        // returns, so throwing here would stall the commit loop and wedge the whole cluster.
        try {
            switch (cmd) {
                case "set":
                    success = put(logEntry);
                    break;
                case "delete":
                    success = delete(logEntry.getKey());
                    break;
                case "clientAttach":
                    success = clientAttach(logEntry);
                    break;
                case "clientOffline":
                    success = clientOffline(logEntry);
                    break;
                case "reapOffline":
                    success = reapOffline(logEntry);
                    break;
                case "noOp":
                    success = true;
                    break;
                default:
                    break;
            }
        } catch (IllegalArgumentException e) {
            log.error("node {} skip log entry with invalid key, index {}: {}",
                    id, logEntry.getIndex(), e.getMessage());
        }
        return success;
    }

    public BPlusNode getRoot(){
        return root;
    }

    /**
     * 按 {@code /} 分隔的路径切换当前节点。绝对路径从 root 出发，相对路径从 position 出发；
     * 段内 {@code ~} 表示 root，{@code .} 表示当前节点，{@code ..} 表示父节点（到 root 为止）。
     * 任一段不存在则返回 {@code null}。
     */
    public BPlusNode cd(String path, BPlusNode position){
        if(path == null || path.isEmpty() || path.equals("/") || path.equals("/root") || path.equals("~")){
            return root;
        }

        BPlusNode current = path.charAt(0) == '/' || path.charAt(0) == '~' ? root : position;
        for (String segment : path.split(KeyPath.SEPARATOR)) {
            if(segment.isEmpty() || segment.equals(".")){
                continue;
            }
            if(segment.equals("~")){
                current = root;
                continue;
            }
            if(segment.equals("..")){
                current = current == root ? root : current.getParent();
                continue;
            }
            if(current.getChildren() == null || !current.getChildren().containsKey(segment)){
                return null;
            }
            current = current.getChildren().get(segment);
        }
        return current;
    }


    public void rebuildParentLinks() {
        rebuildParent(root, null);
    }

    private void rebuildParent(BPlusNode node, BPlusNode parent) {
        if (node == null) return;
        node.setParent(parent);
        if (node.getChildren() != null) {
            for (BPlusNode child : node.getChildren().values()) {
                rebuildParent(child, node);
            }
        }
    }
 

    public boolean persist(){
        String serializedStateMachine = JSON.toJSONString(this);
        persistThread.execute(() -> {
            readWriteLock.writeLock().lock();
            try {
                closeLogWriterQuietly();
                BufferedWriter writer = Files.newBufferedWriter(
                SMFilePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
                writer.write(serializedStateMachine);
                writer.flush();
                writer.close();
            }catch (IOException e) {
                log.error("node {} persist log failed", id);
            }finally{
                readWriteLock.writeLock().unlock();
            }
        });
        return true;
    }

    public void recoverFromLocalImage(){
        try {
            readWriteLock.readLock().lock();
            String nodejson = Files.readString(SMFilePath, StandardCharsets.UTF_8);
            if(nodejson.isBlank()){
                return;
            }
            StateMachine stateMachineImage = JSON.parseObject( nodejson,StateMachine.class);
            this.setRoot(stateMachineImage.getRoot());
            log.info("state machine root path {}", this.getRoot().getPath());
            this.setCommitIndex(stateMachineImage.getCommitIndex());
            // 归属表/租约表随镜像一起恢复；旧版本镜像没有这两个字段，此时保留构造出的空表。
            if(stateMachineImage.getOwnership() != null){
                this.setOwnership(stateMachineImage.getOwnership());
            }
            if(stateMachineImage.getSessions() != null){
                this.setSessions(stateMachineImage.getSessions());
            }
            this.rebuildParentLinks();
            log.info("state machine recover from local image success");
        } catch (IOException e) {
            log.error("node {} recover from image failed", id);
        }finally{
            readWriteLock.readLock().unlock();
        }
    }

    private void openLogWriter(){
        try {
        closeLogWriterQuietly();
        Files.createDirectories(SMFilePath.getParent());
        SMWriter = Files.newBufferedWriter(
            SMFilePath,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
        } catch (IOException e) {
            log.error("node {} open log writer failed", id);
        }
    }

    private void closeLogWriterQuietly() {
        if (SMWriter != null) {
            try {
                SMWriter.close();
            } catch (IOException e) {
                log.error("node {} close log writer failed", id);
            }
            SMWriter = null;
        }
    }

}

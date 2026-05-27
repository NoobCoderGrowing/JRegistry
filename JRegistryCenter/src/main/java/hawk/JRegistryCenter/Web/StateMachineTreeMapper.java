package hawk.JRegistryCenter.Web;

import hawk.JRegitstryCore.BPlusNode;
import hawk.JRegistryCenter.Web.dto.TreeNodeDTO;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class StateMachineTreeMapper {

    private StateMachineTreeMapper() {
    }

    static TreeNodeDTO toTreeNode(BPlusNode node) {
        if (node == null) {
            return null;
        }
        Map<String, BPlusNode> childMap = node.getChildren();
        List<TreeNodeDTO> children = Collections.emptyList();
        if (childMap != null && !childMap.isEmpty()) {
            List<TreeNodeDTO> built = new ArrayList<>(childMap.size());
            childMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> built.add(toTreeNode(entry.getValue())));
            children = built;
        }
        boolean leaf = node.getValue() != null;
        return TreeNodeDTO.builder()
                .key(node.getKey())
                .path(node.getPath())
                .dotKey(toDotKey(node))
                .type(node.getType())
                .value(decodeValue(node.getValue()))
                .leaf(leaf)
                .children(children)
                .build();
    }

    private static String toDotKey(BPlusNode node) {
        String path = node.getPath();
        if (path == null || "/root".equals(path)) {
            return "";
        }
        if (path.startsWith("/root/")) {
            return path.substring("/root/".length()).replace('/', '.');
        }
        return path.replace('/', '.');
    }

    private static String decodeValue(byte[] value) {
        if (value == null) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }
}

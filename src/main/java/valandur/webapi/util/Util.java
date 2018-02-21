package valandur.webapi.util;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.util.Tuple;
import valandur.webapi.WebAPI;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

public class Util {

    public enum ParameterType {
        INT, INTEGER, FLOAT, DOUBLE, BOOL, BOOLEAN, BYTE, CHAR, LONG, SHORT, STRING, CLASS, ENUM,
        VECTOR3D, VECTOR3I, TEXT, WORLD, PLAYER, ITEMSTACK, STATIC,
    }

    private static Map<Class, Field[]> fields = new HashMap<>();
    private static Map<Class, Method[]> methods = new HashMap<>();

    private static SecureRandom random = new SecureRandom();

    public static Tuple<ConfigurationLoader, ConfigurationNode> loadWithDefaults(String path, String defaultPath) {
        try {
            Path filePath = WebAPI.getConfigPath().resolve(path).normalize();
            Optional<Asset> optAsset = Sponge.getAssetManager().getAsset(WebAPI.getInstance(), defaultPath);

            if (!Files.exists(filePath)) {
                filePath.getParent().toFile().mkdirs();
                if (optAsset.isPresent())
                    optAsset.get().copyToDirectory(WebAPI.getConfigPath());
            }

            ConfigurationLoader<CommentedConfigurationNode> loader = HoconConfigurationLoader.builder()
                    .setPath(filePath)
                    .build();
            CommentedConfigurationNode config = loader.load();

            if (optAsset.isPresent()) {
                ConfigurationLoader<CommentedConfigurationNode> defLoader = HoconConfigurationLoader.builder()
                        .setURL(optAsset.get().getUrl())
                        .build();
                CommentedConfigurationNode defConfig = defLoader.load();

                int version = config.getNode("version").getInt(0);
                int defVersion = defConfig.getNode("version").getInt(0);
                boolean newVersion = defVersion != version;

                Util.mergeConfigs(config, defConfig, newVersion);

                if (newVersion) {
                    WebAPI.getLogger().info("New configuration version '" + defVersion + "' for " + path);
                    config.getNode("version").setValue(defVersion);
                }
            }

            loader.save(config);

            return new Tuple<>(loader, config);

        } catch(AccessDeniedException e) {
            WebAPI.getLogger().error("Could not access config file: " + path);
        } catch (IOException | NoSuchElementException e) {
            e.printStackTrace();
            if (WebAPI.reportErrors()) {
                WebAPI.sentryExtra("config", path);
                WebAPI.sentryCapture(e);
            }
        }

        return null;
    }

    /**
     * Generate a random id from a secure number generator
     * @return A random unique id
     */
    public static String generateUniqueId() {
        return new BigInteger(130, random).toString(32);
    }

    /**
     * Checks if the provided string is  valid UUID.
     * @param uuid The string to check.
     * @return True if the provided string is a valid UUID, false otherwise.
     */
    public static boolean isValidUUID(String uuid) {
        return uuid != null && uuid.split("-").length == 5;
    }

    /**
     * Gets the path parts of a given path.
     * @param path The complete path.
     * @return The path parameters.
     */
    public static List<String> getPathParts(String path) {
        if (path == null) return new ArrayList<>();
        return Arrays.stream(path.replaceFirst("^/", "")
                .split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /**
     * Gets the query parameters of a given request.
     * @param req The request from which the query parameters are extracted.
     * @return The query parameters.
     */
    public static Map<String, String> getQueryParams(HttpServletRequest req) {
        Map<String, String> map = new HashMap<>();

        String query = req.getQueryString();
        if (query == null) return map;

        String[] splits = query.split("\\&");
        for (String split : splits) {
            String[] subSplits = split.split("=");
            map.put(subSplits[0], subSplits.length == 2 ? subSplits[1] : "");
        }
        return map;
    }

    /**
     * Transform the first letter to lowercase.
     * @param text The text to transform.
     * @return The transformed text.
     */
    public static String lowerFirst(String text) {
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Returns all NON MINECRAFT NATIVE fields from a class and it's ancestors. (The fields that don't start with "field_")
     * @param c The class for which to get the fields.
     * @return The array of fields of that class and all inherited fields.
     */
    public static Field[] getAllFields(Class c) {
        if (fields.containsKey(c))
            return fields.get(c);

        List<Field> fs = new LinkedList<>();
        while (c != null) {
            fs.addAll(Arrays.asList(c.getDeclaredFields()));
            c = c.getSuperclass();
        }

        fields.put(c, fs.stream().filter(f -> !f.getName().startsWith("field_")).toArray(Field[]::new));
        return fields.get(c);
    }

    /**
     * Returns all NON MINECRAFT NATIVE methods from a class and it's ancestors. (The methods that don't start with "func_")
     * @param c The class for which to get the methods
     * @return The array of methods of that class and all inherited methods.
     */
    public static Method[] getAllMethods(Class c) {
        if (methods.containsKey(c))
            return methods.get(c);

        List<Method> ms = new LinkedList<>();
        while (c != null) {
            ms.addAll(Arrays.asList(c.getDeclaredMethods()));
            c = c.getSuperclass();
        }

        methods.put(c, ms.stream().filter(m -> !m.getName().startsWith("func_")).toArray(Method[]::new));
        return methods.get(c);
    }

    /**
     * Merge default values into a existing config, adding only the values that don't exist yet.
     * @param node The node that receives any missing default values
     * @param def The default configuration
     * @param restoreDefaults True if missing values in the config marked with _DEFAULT_ in the comment of the default config should be added.
     */
    public static void mergeConfigs(CommentedConfigurationNode node,
                                    CommentedConfigurationNode def,
                                    boolean restoreDefaults) {
        if (def.getComment().isPresent() && def.getComment().get().contains("_EXAMPLE_") && !restoreDefaults) {
            return;
        }

        if (node.isVirtual()) {
            node.setValue(def);
            return;
        }

        if (!def.hasMapChildren()) {
            return;
        }

        Map<Object, ? extends CommentedConfigurationNode> map = def.getChildrenMap();
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : map.entrySet()) {
            CommentedConfigurationNode subNode = node.getNode(entry.getKey());
            mergeConfigs(subNode, entry.getValue(), restoreDefaults);
        }
    }
}

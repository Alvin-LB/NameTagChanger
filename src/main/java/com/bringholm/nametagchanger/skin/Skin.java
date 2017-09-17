package com.bringholm.nametagchanger.skin;

import com.google.common.collect.Maps;
import org.apache.commons.lang.Validate;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores information about a minecraft user's skin.
 *
 * This class does implement ConfigurationSerializable,
 * which means that you can use it to save skins in
 * config, but do however note that for the class to
 * be registered correctly, you should always call
 * NameTagChanger.INSTANCE.enable() in your onEnable()
 * (not before checking if it is already enabled, of
 * course) and call NameTagChanger.INSTANCE.disable()
 * in your onDisable (and again, check if NameTagChanger
 * is already disabled first).
 *
 * @author AlvinB
 */
public class Skin implements ConfigurationSerializable {
    public static final Skin EMPTY_SKIN = new Skin();

    private UUID uuid;
    private String base64;
    private String signedBase64;

    /**
     * Initializes this class with the specified skin.
     *
     * @param uuid The uuid of the user who this skin belongs to
     * @param base64 the base64 data of the skin, as returned by Mojang's servers.
     * @param signedBase64 the signed data of the skin, as returned by Mojang's servers.
     */
    public Skin(UUID uuid, String base64, String signedBase64) {
        Validate.notNull(uuid, "uuid cannot be null");
        Validate.notNull(base64, "base64 cannot be null");
        this.uuid = uuid;
        this.base64 = base64;
        this.signedBase64 = signedBase64;
    }

    private Skin() {}

    public boolean hasSignedBase64() {
        return signedBase64 != null;
    }

    public String getSignedBase64() {
        return signedBase64;
    }

    public String getBase64() {
        return base64;
    }

    public UUID getUUID() {
        return uuid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Skin)) {
            return false;
        }
        Skin skin = (Skin) obj;
        if (skin == Skin.EMPTY_SKIN) {
            return this == Skin.EMPTY_SKIN;
        }
        return skin.base64.equals(this.base64) && skin.uuid.equals(this.uuid) && skin.signedBase64.equals(this.signedBase64);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.base64, this.uuid, this.signedBase64);
    }

    @Override
    public String toString() {
        return "Skin{uuid=" + uuid + ",base64=" + base64 + ",signedBase64=" + signedBase64 + "}";
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = Maps.newHashMap();
        if (this == EMPTY_SKIN) {
            map.put("empty", "true");
        } else {
            map.put("uuid", uuid);
            map.put("base64", base64);
            if (hasSignedBase64()) {
                map.put("signedBase64", signedBase64);
            }
        }
        return map;
    }

    public static Skin deserialize(Map<String, Object> map) {
        if (map.containsKey("empty")) {
            return EMPTY_SKIN;
        } else {
            return new Skin(UUID.fromString((String) map.get("uuid")), (String) map.get("base64"), (map.containsKey("signedBase64") ? (String) map.get("signedBase64") : null));
        }
    }
}

package com.lucyago.instinctpassive;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only comparison against Libre 3 GATT UUIDs documented by Juggluco.
 * Matching UUIDs indicate a compatible service layout, NOT a verified sensor
 * identity, authenticated session, or working glucose decoder.
 */
public final class Libre3GattFingerprint {
    public static final UUID DATA_SERVICE=UUID.fromString("089810cc-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID SECURITY_SERVICE=UUID.fromString("0898203a-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID GLUCOSE=UUID.fromString("0898177a-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID PATCH_CONTROL=UUID.fromString("08981338-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID COMMAND_RESPONSE=UUID.fromString("08982198-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID CHALLENGE=UUID.fromString("089822ce-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID CERTIFICATE=UUID.fromString("089823fa-ef89-11e9-81b4-2a2ae2dbcce4");

    public static final class Result {
        public final boolean dataService, securityService, glucose, control, command, challenge, certificate;
        Result(Set<UUID> services,Set<UUID> characteristics) {
            dataService=services.contains(DATA_SERVICE);
            securityService=services.contains(SECURITY_SERVICE);
            glucose=characteristics.contains(GLUCOSE);
            control=characteristics.contains(PATCH_CONTROL);
            command=characteristics.contains(COMMAND_RESPONSE);
            challenge=characteristics.contains(CHALLENGE);
            certificate=characteristics.contains(CERTIFICATE);
        }
        public boolean hasExpectedLayout() {
            return dataService && securityService && glucose && control &&
                command && challenge && certificate;
        }
        public String summary() {
            return "Comparación UUID Libre 3: servicio datos="+dataService+
                ", seguridad="+securityService+", glucosa="+glucose+
                ", control="+control+", comando="+command+
                ", reto="+challenge+", certificado="+certificate+
                ". Disposición esperada="+hasExpectedLayout()+
                ". NO demuestra compatibilidad criptográfica ni identidad Instinct Go.";
        }
    }
    private Libre3GattFingerprint() {}
    public static Result inspect(Collection<UUID> serviceUuids,Collection<UUID> characteristicUuids) {
        if(serviceUuids==null || characteristicUuids==null)
            throw new IllegalArgumentException("Missing UUIDs");
        return new Result(new HashSet<>(serviceUuids),new HashSet<>(characteristicUuids));
    }
}

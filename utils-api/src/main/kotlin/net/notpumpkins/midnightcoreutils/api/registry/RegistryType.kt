package net.notpumpkins.midnightcoreutils.api.registry

enum class RegistryType(val registryName: String) {
    ITEM("item"),
    BLOCK("block"),
    ENTITY("entity"),
    BLOCK_ENTITY("block_entity"),
    MENU("menu"),
    RECIPE_SERIALIZER("recipe_serializer"),
    SOUND_EVENT("sound_event"),
    POTION("potion"),
    ENCHANTMENT("enchantment"),
    PARTICLE("particle")
}

package com.github.quillraven.fleks

import kotlin.reflect.KClass

class WorldConfiguration {
    var entityCapacity = 512

    @PublishedApi
    internal val systemTypes = mutableListOf<KClass<out EntitySystem>>()

    @PublishedApi
    internal val injectables = mutableMapOf<KClass<*>, Any>()

    inline fun <reified T : EntitySystem> system() {
        val systemType = T::class
        if (systemType in systemTypes) {
            throw FleksSystemAlreadyAddedException(systemType)
        }
        systemTypes.add(systemType)
    }

    inline fun <reified T : Any> inject(value: T) {
        val injectType = T::class
        if (injectType in injectables) {
            throw FleksInjectableAlreadyAddedException(injectType)
        }
        injectables[injectType] = value
    }
}

class World(
    cfg: WorldConfiguration.() -> Unit
) {
    private val systemService: SystemService

    @PublishedApi
    internal val componentService = ComponentService()

    @PublishedApi
    internal val entityService: EntityService

    init {
        val worldCfg = WorldConfiguration().apply(cfg)
        entityService = EntityService(worldCfg.entityCapacity, componentService)
        systemService = SystemService(this, worldCfg.systemTypes, worldCfg.injectables)
    }

    inline fun entity(cfg: EntityConfiguration.() -> Unit = {}): Int {
        return entityService.create(cfg)
    }

    inline fun configureEntity(entityId: Int, cfg: EntityConfiguration.() -> Unit) {
        return entityService.configureEntity(entityId, cfg)
    }

    fun remove(entityId: Int) {
        entityService.remove(entityId)
    }

    fun update(deltaTime: Float) {
        systemService.update(deltaTime.coerceAtMost(1 / 30f))
    }
}

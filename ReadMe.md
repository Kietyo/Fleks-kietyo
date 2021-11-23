# Fleks

[![MIT license](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/Quillraven/Fleks/blob/master/LICENSE)
[![Maven](https://badgen.net/badge/icon/maven?icon=maven&label=preRelease-20211120)](https://search.maven.org/artifact/io.github.quillraven.fleks/Fleks)

[![Build Master](https://img.shields.io/github/workflow/status/quillraven/fleks/Build/master?event=push&label=Build%20master)](https://github.com/Quillraven/fleks/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.6.0-red.svg)](http://kotlinlang.org/)

A **f**ast, **l**ightweight, **e**ntity component **s**ystem library written in **K**otlin.

## Motivation

When developing my hobby games using [LibGDX](https://github.com/libgdx/libgdx), I always
used [Ashley](https://github.com/libgdx/ashley)
as an [**E**ntity **C**omponent **S**ystem](https://en.wikipedia.org/wiki/Entity_component_system) since it comes out of
the box with LibGDX and performance wise it was always good enough for me.

When using [Kotlin](https://kotlinlang.org/) and [LibKTX](https://github.com/libktx/ktx) you even get nice extension
functions for it but I never was fully happy with how it felt because:

- Defining [ComponentMapper](https://github.com/libgdx/ashley/wiki/How-to-use-Ashley#retrieving-components-with-componentmapper)
  for every [Component](https://github.com/libgdx/ashley/wiki/How-to-use-Ashley#components) felt very redundant
- Ashley is not null-safe and therefore you get e.g. `Entity?` passed in as default to
  an [IteratingSystem](https://github.com/libgdx/ashley/wiki/Built-in-Entity-Systems#iteratingsystem)
  although it will never be null (or at least shouldn't 😉)
- Creating [Families](https://github.com/libgdx/ashley/wiki/How-to-use-Ashley#entity-families) as constructor arguments
  felt weird to me
- The creator seems to not work on this project anymore or at least reacts super slowly to change requests, bugs or
  improvement ideas
- Although for me it was never a problem, I heard from other people that the performance is sometimes bad especially
  with a lot of [entities](https://github.com/libgdx/ashley/wiki/How-to-use-Ashley#entities) that get their components
  updated each frame

Those are the reasons why I wanted to build my own ECS-library and of course out of interest to dig deep into the
details of this topic and to learn something new!

## Who should use this library?

If you need a lightweight and fast ECS in your Kotlin application then feel free to use Fleks.

If you are looking for a long time verified ECS that supports Java 
then use [Artemis-odb](https://github.com/junkdog/artemis-odb) or [Ashley](https://github.com/libgdx/ashley).

## Current Status

There is a pre-release available since 2021-11-20. Please feel free to contribute to the
Discussions or Issues. Help is always appreciated. To use the pre-release add following lines to your `build.gradle` file.

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.quillraven.fleks:Fleks:preRelease-20211120")
}
```

## Current API and usage

### World

The core of Fleks is the `World` which is the container for entities, components and systems and is the object that you
need to update your systems.

To create a world simply call:

```Kotlin
val world = World {}
```

A world without any **system** doesn't make sense and that's why there is a lambda argument for the world's constructor
to configure it accordingly:

- Use `entityCapacity` to set the expected maximum amount of entities. The default value is 512. The reason for this
  setting is to initialize internal collections and arrays with a proper size for your game to avoid a lot
  of `Array.copy` calls in the background which are slow.
- Use `system` to add a system to your world. The order of `system` calls defines the order in which they are called
  when calling `world.update`

Here is an example that creates a world for 1000 entities with a Move- and PhysicSystem:

```Kotlin
val world = World {
    entityCapacity = 1000

    system<MoveSystem>()
    system<PhysicSystem>()
}
```

### System

Usually, your systems depend on certain other things like
a [SpriteBatch](https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites#SpriteBatch)
or [Viewport](https://github.com/libgdx/libgdx/wiki/Spritebatch%2C-Textureregions%2C-and-Sprites#Viewport). Fleks
uses [dependency injection](https://de.wikipedia.org/wiki/Dependency_Injection) for that to make it easier to adjust
arguments of your systems later on without touching the code of the caller side.

First, let's have a look on how to create a simple **IteratingSystem** that gets called every time `world.update` is
called. It is a made up example of a Day-Night-Cycle system which switches between day and night every second and
dispatches a game event via an `EventManager`.

```Kotlin
class DayNightSystem(
    private val eventMgr: EventManager
) : IntervalSystem() {
    private var currentTime = 0f
    private var isDay = false

    override fun onTick() {
        // deltaTime is not needed in every system that's why it is not a parameter of "onTick".
        // However, if you need it, you can still access it via the IteratingSystem's deltaTime property
        currentTime += deltaTime
        if (currentTime >= 1000 && !isDay) {
            isDay = true
            eventMgr.publishDayEvent()
        } else if (currentTime >= 2000 && isDay) {
            isDay = false
            currentTime = 0f
            eventMgr.publishNightEvent()
        }
    }
}
```

The `DayNightSystem` requires an `EventManager` which we need to inject. To achieve that we can define it when creating
our world by using the `inject` function:

```Kotlin
val eventManager = EventManager()
val world = World {
    entityCapacity = 1000

    system<DayNightSystem>()

    inject(eventManager)
}
```

There are two systems in Fleks:

- `IntervalSystem`: system without relation to entities
- `IteratingSystem`: system with relation to entities of a specific component configuration

`IntervalSystem` has two optional arguments:

- `interval`: defines the time in milliseconds when the system should be updated. Default is `EachFrame` which means
  that it gets called every time `world.update` is called
    - The other interval option is `Fixed` which takes a step time in milliseconds and runs the system with that fixed
      time step
- `enabled`: defines if the system will be processed or not. Default value is true

`IteratingSystem` extends `IntervalSystem` but in addition it requires you to specify the relevant components of
entities which the system will iterate over. There are three class annotations to define this component configuration:

- `AllOf`: entity must have all the components specified
- `NoneOf`: entity must not have any component specified
- `AnyOf`: entity must have at least one of the components specified

Let's create an `IteratingSystem` that iterates over all entities with a `PositionComponent`, `PhysicComponent`
and at least a `SpriteComponent` or `AnimationComponent` but without a `DeadComponent`:

```Kotlin
@AllOf([Position::class, Physic::class])
@NoneOf([Dead::class])
@AnyOf([Sprite::class, Animation::class])
class AnimationSystem : IteratingSystem() {
    override fun onTickEntity(entity: Entity) {
        // update entities in here
    }
}
```

Often, an `IteratingSystem` needs access to the components of an entity. In Fleks this is done via so
called `ComponentMapper`. `ComponentMapper` are automatically injected into a system and do not need to be defined in
the world's configuration.

Let's see how we can access the `PositionComponent` of an entity in the system above:

```Kotlin
@AllOf([Position::class, Physic::class])
@NoneOf([Dead::class])
@AnyOf([Sprite::class, Animation::class])
class AnimationSystem(
    private val positions: ComponentMapper<Position>
) : IteratingSystem() {
    override fun onTickEntity(entity: Entity) {
        val entityPosition: Position = positions[entity]
    }
}
```

If you need to modify the component configuration of an entity then this can be done via the `configureEntity` function
of an `IteratingSystem`. The purpose of this function is performance reasons to trigger internal calculations
of Fleks only once instead of each time a component gets added or removed.

Let's see how a system can look like that adds a `DeadComponent` to an entity and removes a `LifeComponent` when its
hitpoints are <= 0:

```Kotlin
@AllOf([Life::class])
@NoneOf([Dead::class])
class DeathSystem(
    private val lives: ComponentMapper<Life>,
    private val deads: ComponentMapper<Dead>
) : IteratingSystem() {
    override fun onTickEntity(entity: Entity) {
        if (lives[entity].hitpoints <= 0f) {
            configureEntity(entity) {
                deads.add(it)
                lives.remove(it)
            }
        }
    }
}
```

Sometimes it might be necessary to sort entities before iterating over them like e.g. in a `RenderSystem` that needs to
render entities by their y or z-coordinate. In Fleks this can be achieved by passing an `EntityComparator` to
an `IteratingSystem`. Entities are then sorted automatically every time the system gets updated. The `compareEntity`
function helps to create such a comparator in a concise way.

Here is an example of a `RenderSystem` that sorts entities by their y-coordinate:

```Kotlin
@AllOf([Position::class, Render::class])
class RenderSystem(
    private val positions: ComponentMapper<Position>
) : IteratingSystem(compareEntity { entA, entB -> positions[entA].y.compareTo(positions[entB].y) }) {
    override fun onTickEntity(entity: Entity) {
        // render entities: entities are sorted by their y-coordinate
    }
}
```

The default `SortingType` is `Automatic` which means that the `IteratingSystem` is sorting automatically each time it
gets updated. This can be changed to `Manual` by setting the `sortingType` parameter accordingly. In that case
the `doSort` flag of the `IteratingSystem`
needs to be set programmatically whenever sorting should be done. The flag gets cleared after the sorting.

This is how the example above could be written with a `Manual` `SortingType`:

```Kotlin
@AllOf([Position::class, Render::class])
class RenderSystem(
    private val positions: ComponentMapper<Position>
) : IteratingSystem(
    compareEntity { entA, entB -> positions[entA].y.compareTo(positions[entB].y) },
    sortingType = Manual
) {
    override fun onTick() {
        doSort = true
        super.onTick()
    }

    override fun onTickEntity(entity: Entity) {
        // render entities: entities are sorted by their y-coordinate
    }
}
```

### Entity and Components

We now know how to create a world and add systems to it, but we don't know how to add entities to our world. This can be
done via the `entity` function of the world. Let's create an entity with a `PositionComponent` and `SpriteComponent`:

```Kotlin
data class Position(var x: Float = 0f, var y: Float = 0f)

data class Sprite(var texturePath: String = "")

fun main() {
    val world = World {}

    val entity: Entity = world.entity {
        add<Position> { x = 5f }
        add<Sprite>()
    }
}
```

## Performance

One important topic for me throughout the development of Fleks was performance. For that I compared Fleks with
Artemis-odb and Ashley in three scenarios which you can find in the **benchmarks** source set:

1) **AddRemove**: Creates 10_000 entities with a single component each and removes those entities.
2) **Simple**: Steps the world 1_000 times for 10_000 entities with an `IteratingSystem` for a single component that
   gets a `Float` counter increased by one every tick.
3) **Complex**: Steps the world 1_000 times for 10_000 entities with two `IteratingSystem` and three components. It is a
   time-consuming benchmark because all entities get added and removed from the first system each tick.
    - Each entity gets initialized with ComponentA and ComponentC.
    - The first system requires ComponentA, ComponentC and not ComponentB. It switches between creating ComponentB or
      removing ComponentA. That way every entity gets removed from this system each tick.
    - The second system requires any ComponentA/B/C and removes ComponentB and adds ComponentA. That way every entity
      gets added again to the first system.

I used [kotlinx-benchmark](https://github.com/Kotlin/kotlinx-benchmark) to create the benchmarks with a measurement
that represents the number of executed operations within three seconds.

All Benchmarks are run within IntelliJ using the `benchmarksBenchmark` gradle task on my local computer. The hardware
is:

- Windows 10 64-bit
- 16 GB Ram
- Intel i7-5820K @ 3.30Ghz
- Java 8 target
- Kotlin 1.5.31

Here is the result (the higher the Score the better):

| Library | Benchmark | Mode | Cnt | Score | Error | Units |
| ------- | --------- | ---- | --- | ----- | ----- | ----- |
| |
| Ashley | AddRemove | thrpt | 3 | 207,007 | ± 39,121 | ops/s |
| Artemis | AddRemove | thrpt | 3 | 677,231 | ± 2002,449 | ops/s |
| Fleks | AddRemove | thrpt | 3 | 806,189 | ± 249,523 | ops/s |
| |
| Ashley | Simple | thrpt | 3 | 3,986 | ± 1,390 | ops/s |
| Artemis | Simple | thrpt | 3 | 32,830 | ± 2,965 | ops/s |
| Fleks | Simple | thrpt | 3 | 32,639 | ± 5,651 | ops/s |
| |
| Ashley | Complex | thrpt | 3 | 0,056 | ± 0,117 | ops/s |
| Artemis | Complex | thrpt | 3 | 1,452 | ± 0,452 | ops/s |
| Fleks | Complex | thrpt | 3 | 1,196 | ± 0,210 | ops/s |

I am not an expert for performance measurement, that's why you should take those numbers with a grain of salt but as you
can see in the table:

- Ashley is the slowest of the three libraries by far
- Fleks is ~1.2x the speed of Artemis in the **AddRemove** benchmark
- Fleks is ~the same speed as Artemis in the **Simple** benchmark
- Fleks is ~0.8x the speed of Artemis in the **Complex** benchmark

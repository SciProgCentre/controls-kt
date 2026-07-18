package space.kscience.controls.models


import space.kscience.controls.constructor.units.Kilograms
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.manager.createDeviceTree
import space.kscience.controls.models.continuous.ContinuousModelLibrary
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.meta.ListValue
import space.kscience.dataforge.meta.Meta
import kotlin.test.Test
import kotlin.test.assertNotNull

class LibraryFlowModelTest {

    @Test
    fun testChemicalFactoryNoTransform() {
        val library = ContinuousModelLibrary(Kilograms)
        val context = Context {
            plugin(DeviceManager.Companion)
        }
        val deviceManager = context.request(DeviceManager.Companion)

        val meta = Meta {
            "type" put "flowModel"
            "parameters" put {
                "models" put {
                    "aProducer" put {
                        "type" put "producer"
                        "parameters" put {
                            "productionCapacity" put 1.0
                        }
                    }
                    "bProducer" put {
                        "type" put "producer"
                        "parameters" put {
                            "productionCapacity" put 1.5
                        }
                    }
                    "mixer" put {
                        "type" put "mix"
                        "parameters" put {
                            "supplyKeys" put ListValue("a", "b")
                        }
                    }
                    "abBuffer" put {
                        "type" put "buffer"
                        "parameters" put {
                            "capacity" put 10.0
                        }
                    }
                    "cProducer" put {
                        "type" put "producer"
                        "parameters" put {
                            "productionCapacity" put 10.0
                        }
                    }
                    "cBuffer" put {
                        "type" put "buffer"
                        "parameters" put {
                            "capacity" put 50.0
                        }
                    }
                    "reactor" put {
                        "type" put "reaction"
                        "parameters" put {
                            "formula" put {
                                "ab" put 1.0
                                "c" put 1.0
                            }
                            "productionCapacity" put 1.0
                        }
                    }
                    "consumer" put {
                        "type" put "consumer"
                        "parameters" put {
                            "consumationCapacity" put 2.0
                        }
                    }
                }
                "flowBindings" putIndexed listOf(
                    Meta {
                        "producer" put "aProducer"
                        "consumer" put "mixer.a"
                    },
                    Meta {
                        "producer" put "bProducer"
                        "consumer" put "mixer.b"
                    },
                    Meta {
                        "producer" put "mixer"
                        "consumer" put "abBuffer"
                    },
                    Meta {
                        "producer" put "cProducer"
                        "consumer" put "cBuffer"
                    },
                    Meta {
                        "producer" put "abBuffer"
                        "consumer" put "reactor.ab"
                    },
                    Meta {
                        "producer" put "cBuffer"
                        "consumer" put "reactor.c"
                    },
                    Meta {
                        "producer" put "reactor"
                        "consumer" put "consumer"
                    }
                )

            }
        }

        val model = deviceManager.createDeviceTree(meta, library.factories)
        assertNotNull(model)
    }
}
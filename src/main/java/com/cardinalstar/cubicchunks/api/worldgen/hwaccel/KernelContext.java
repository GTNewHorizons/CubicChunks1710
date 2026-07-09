package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memAllocLong;
import static org.lwjgl.system.MemoryUtil.memAllocPointer;
import static org.lwjgl.util.vma.Vma.vmaCreateAllocator;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTDebugUtils.vkCreateDebugUtilsMessengerEXT;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_ERROR_EXTENSION_NOT_PRESENT;
import static org.lwjgl.vulkan.VK10.VK_ERROR_INCOMPATIBLE_DRIVER;
import static org.lwjgl.vulkan.VK10.VK_ERROR_OUT_OF_HOST_MEMORY;
import static org.lwjgl.vulkan.VK10.VK_FALSE;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_CPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkEnumerateInstanceExtensionProperties;
import static org.lwjgl.vulkan.VK10.vkEnumerateInstanceLayerProperties;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceFeatures;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

import java.io.File;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.Level;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import com.cardinalstar.cubicchunks.CubicChunks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Getter;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

@Lwjgl3Aware
public class KernelContext {

    private static final boolean ENABLE_VALIDATION = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")
        || Boolean.parseBoolean(
            System.getProperty("cubicchunks.compute.validate", "false")
                .toLowerCase());
    /// Approximately 1 MB
    public static final int CHUNK_SIZE = 1 << 20;

    @Getter
    private static boolean enabled;

    private static Thread worker;

    @Getter
    private static KernelScheduler scheduler;

    private static final IntBuffer IP = memAllocInt(1);
    private static final LongBuffer LP = memAllocLong(1);
    private static final PointerBuffer PP = memAllocPointer(1);

    private static VkInstance instance;
    private static long messageCallback;
    private static VkPhysicalDevice gpu;
    @Getter
    private static int computeQueueFamily = -1;
    private static VkDevice device;
    @Getter
    private static VkQueue computeQueue;
    @Getter
    private static long vmaAllocator;

    @SideOnly(Side.CLIENT)
    public static void initClient() {
        startWorkerThread();
    }

    @SideOnly(Side.SERVER)
    public static void initServer() {
        startWorkerThread();
    }

    private static void startWorkerThread() {
        worker = new Thread(KernelContext::init);
        worker.setName("CC-WG-Dispatcher");
        worker.setDaemon(true);
        worker.start();
    }

    private static void init() {
        createInstance();
        File cacheDir = new File(Launch.minecraftHome, "config/cc-cache");
        ShaderCache.init(cacheDir);
        SpirVCompiler.init();
        scanPhysicalDevices();
        selectQueueFamily();
        createLogicalDevice();
        createVmaAllocator();
        VulkanPipelineCache.init(cacheDir);

        CubicChunks.LOGGER.info("Successfully created offscreen compute context");

        enabled = true;
        scheduler = new KernelScheduler();

        try {
            scheduler.run();
        } catch (Throwable t) {
            CubicChunks.LOGGER.error("CC-WG-Dispatcher failed", t);
        } finally {
            try {
                scheduler.close();
            } catch (Throwable t) {
                CubicChunks.LOGGER.error("Could not clean up KernelScheduler", t);
            }

            VulkanPipelineCache.destroy();
            SpirVCompiler.destroy();
            vmaDestroyAllocator(vmaAllocator);
            vkDestroyDevice(device, null);
            vkDestroyInstance(instance, null);
        }
    }

    private static final VkDebugUtilsMessengerCallbackEXT dbgFunc = VkDebugUtilsMessengerCallbackEXT
        .create((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
            Level severity;
            if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT) != 0) {
                severity = Level.TRACE;
            } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                severity = Level.INFO;
            } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                severity = Level.WARN;
            } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                severity = Level.ERROR;
            } else {
                severity = Level.DEBUG;
            }

            String type;
            if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT) != 0) {
                type = "GENERAL";
            } else if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT) != 0) {
                type = "VALIDATION";
            } else if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT) != 0) {
                type = "PERFORMANCE";
            } else {
                type = "UNKNOWN";
            }

            VkDebugUtilsMessengerCallbackDataEXT data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

            KernelScheduler.LOGGER
                .log(severity, "[{}:{}] {}", type, data.pMessageIdNameString(), data.pMessageString());

            /*
             * false indicates that layer should not bail-out of an
             * API call that had validation failures. This may mean that the
             * app dies inside the driver due to invalid parameter(s).
             * That's what would happen without validation layers, so we'll
             * keep that behavior here.
             */
            return VK_FALSE;
        });

    public static VkDevice getDevice() {
        return device;
    }

    private static PointerBuffer checkLayers(MemoryStack stack, Set<String> availableLayers, String... requiredLayers) {
        for (String req : requiredLayers) {
            if (!availableLayers.contains(req)) return null;
        }

        PointerBuffer buffer = stack.mallocPointer(requiredLayers.length);

        for (int i = 0; i < requiredLayers.length; i++) {
            buffer.put(i, stack.ASCII(requiredLayers[i]));
        }

        return buffer;
    }

    private static void createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer ppEnabledLayerNames = null;

            if (ENABLE_VALIDATION) {
                check(vkEnumerateInstanceLayerProperties(IP, null));

                if (IP.get(0) > 0) {
                    VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(IP.get(0), stack);
                    check(vkEnumerateInstanceLayerProperties(IP, availableLayers));

                    Set<String> layers = availableLayers.stream()
                        .map(VkLayerProperties::layerNameString)
                        .collect(Collectors.toSet());

                    // VulkanSDK 1.1.106+
                    ppEnabledLayerNames = checkLayers(stack, layers, "VK_LAYER_KHRONOS_validation"
                    // ,"VK_LAYER_LUNARG_assistant_layer"
                    );
                    if (ppEnabledLayerNames == null) { // use alternative (deprecated) set of validation layers
                        ppEnabledLayerNames = checkLayers(stack, layers, "VK_LAYER_LUNARG_standard_validation"
                        // ,"VK_LAYER_LUNARG_assistant_layer"
                        );
                    }
                    if (ppEnabledLayerNames == null) { // use alternative (deprecated) set of validation layers
                        ppEnabledLayerNames = checkLayers(
                            stack,
                            layers,
                            "VK_LAYER_GOOGLE_threading",
                            "VK_LAYER_LUNARG_parameter_validation",
                            "VK_LAYER_LUNARG_object_tracker",
                            "VK_LAYER_LUNARG_core_validation",
                            "VK_LAYER_GOOGLE_unique_objects"
                        // ,"VK_LAYER_LUNARG_assistant_layer"
                        );
                    }
                }

                if (ppEnabledLayerNames == null) {
                    throw new IllegalStateException(
                        "vkEnumerateInstanceLayerProperties failed to find required validation layer.");
                }
            }

            check(vkEnumerateInstanceExtensionProperties((String) null, IP, null));

            List<String> requiredInstanceExtensions = new ArrayList<>();
            requiredInstanceExtensions.add(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);

            if (IP.get(0) > 0) {
                VkExtensionProperties.Buffer instance_extensions = VkExtensionProperties.malloc(IP.get(0), stack);
                check(vkEnumerateInstanceExtensionProperties((String) null, IP, instance_extensions));

                Set<String> availableExtensions = instance_extensions.stream()
                    .map(VkExtensionProperties::extensionNameString)
                    .collect(Collectors.toSet());

                if (availableExtensions.contains(VK_EXT_DEBUG_UTILS_EXTENSION_NAME) && ENABLE_VALIDATION) {
                    requiredInstanceExtensions.add(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                }
            }

            PointerBuffer ppEnabledExtensionNames = stack.mallocPointer(requiredInstanceExtensions.size());

            for (String ext : requiredInstanceExtensions) {
                ppEnabledExtensionNames.put(stack.UTF8(ext));
            }

            ppEnabledExtensionNames.flip();

            VkInstanceCreateInfo pCreateInfo = VkInstanceCreateInfo.calloc(stack)
                .sType$Default()
                .pApplicationInfo(
                    VkApplicationInfo.calloc(stack)
                        .sType$Default()
                        .pApplicationName(stack.UTF8("Cubic Chunks Compute"))
                        .apiVersion(VK_API_VERSION_1_2))
                .ppEnabledLayerNames(ppEnabledLayerNames)
                .ppEnabledExtensionNames(ppEnabledExtensionNames);

            VkDebugUtilsMessengerCreateInfoEXT dbgCreateInfo = null;

            if (ENABLE_VALIDATION) {
                dbgCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.malloc(stack)
                    .sType$Default()
                    .messageSeverity(
                        /*
                         * VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                         * VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT |
                         */
                        VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                    .messageType(
                        VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                            | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                    .pfnUserCallback(dbgFunc);

                pCreateInfo.pNext(dbgCreateInfo);
            }

            int err = vkCreateInstance(pCreateInfo, null, PP);

            if (err == VK_ERROR_INCOMPATIBLE_DRIVER) {
                throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD).");
            } else if (err == VK_ERROR_EXTENSION_NOT_PRESENT) {
                throw new IllegalStateException(
                    "Cannot find a specified extension library. Make sure your layers path is set appropriately.");
            } else if (err != 0) {
                throw new IllegalStateException(
                    "vkCreateInstance failed. Do you have a compatible Vulkan installable client driver (ICD) installed?");
            }

            instance = new VkInstance(PP.get(0), pCreateInfo);

            if (ENABLE_VALIDATION) {
                err = vkCreateDebugUtilsMessengerEXT(instance, dbgCreateInfo, null, LP);
                switch (err) {
                    case VK_SUCCESS:
                        messageCallback = LP.get(0);
                        break;
                    case VK_ERROR_OUT_OF_HOST_MEMORY:
                        throw new IllegalStateException("CreateDebugReportCallback: out of host memory");
                    default:
                        throw new IllegalStateException("CreateDebugReportCallback: unknown failure");
                }
            }
        }
    }

    private static void scanPhysicalDevices() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            /* Make initial call to query gpu_count, then second call for gpu info */
            check(vkEnumeratePhysicalDevices(instance, IP, null));

            if (IP.get(0) > 0) {
                PointerBuffer physical_devices = stack.mallocPointer(IP.get(0));
                check(vkEnumeratePhysicalDevices(instance, IP, physical_devices));

                VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);

                VkPhysicalDevice best = null;
                long bestScore = -1;

                for (int i = 0; i < IP.get(0); i++) {
                    VkPhysicalDevice device = new VkPhysicalDevice(physical_devices.get(i), instance);

                    vkGetPhysicalDeviceFeatures(device, features);
                    vkGetPhysicalDeviceProperties(device, props);
                    vkGetPhysicalDeviceMemoryProperties(device, memProps);

                    long score = scoreDevice(props, memProps);
                    CubicChunks.LOGGER.info(
                        "Vulkan device [{}]: type={}, score={}",
                        props.deviceNameString(),
                        props.deviceType(),
                        score);

                    if (score > bestScore) {
                        bestScore = score;
                        best = device;
                    }
                }

                if (best == null) {
                    throw new IllegalStateException("No suitable Vulkan physical device found.");
                }

                gpu = best;
            } else {
                throw new IllegalStateException("vkEnumeratePhysicalDevices reported zero accessible devices.");
            }
        }
    }

    private static long scoreDevice(VkPhysicalDeviceProperties props, VkPhysicalDeviceMemoryProperties memProps) {
        long score = switch (props.deviceType()) {
            case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 4L << 40;
            case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 3L << 40;
            case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> 2L << 40;
            case VK_PHYSICAL_DEVICE_TYPE_CPU -> 1L << 40;
            default -> 0L;
        };

        for (int h = 0; h < memProps.memoryHeapCount(); h++) {
            VkMemoryHeap heap = memProps.memoryHeaps(h);
            if ((heap.flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
                score += heap.size() >> 20; // MiB of VRAM as tiebreaker
            }
        }

        return score;
    }

    private static void selectQueueFamily() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkGetPhysicalDeviceQueueFamilyProperties(gpu, IP, null);
            int count = IP.get(0);

            if (count == 0) {
                throw new IllegalStateException("No Vulkan queue families found.");
            }

            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count, stack);
            vkGetPhysicalDeviceQueueFamilyProperties(gpu, IP, families);

            int dedicatedComputeFamily = -1;
            int generalComputeFamily = -1;

            for (int i = 0; i < count; i++) {
                int flags = families.get(i)
                    .queueFlags();
                boolean compute = (flags & VK_QUEUE_COMPUTE_BIT) != 0;
                boolean graphics = (flags & VK_QUEUE_GRAPHICS_BIT) != 0;

                if (compute && !graphics && dedicatedComputeFamily == -1) {
                    dedicatedComputeFamily = i;
                } else if (compute && generalComputeFamily == -1) {
                    generalComputeFamily = i;
                }
            }

            if (dedicatedComputeFamily != -1) {
                computeQueueFamily = dedicatedComputeFamily;
                CubicChunks.LOGGER.info("Selected dedicated compute queue family {}", computeQueueFamily);
            } else if (generalComputeFamily != -1) {
                computeQueueFamily = generalComputeFamily;
                CubicChunks.LOGGER.info("Selected general (graphics+compute) queue family {}", computeQueueFamily);
            } else {
                throw new IllegalStateException("No compute-capable Vulkan queue family found.");
            }
        }
    }

    private static void createLogicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueCreateInfos.get(0)
                .sType$Default()
                .queueFamilyIndex(computeQueueFamily)
                .pQueuePriorities(stack.floats(1.0f));

            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType$Default()
                .pQueueCreateInfos(queueCreateInfos)
                .pEnabledFeatures(VkPhysicalDeviceFeatures.calloc(stack));

            check(vkCreateDevice(gpu, deviceCreateInfo, null, PP));
            device = new VkDevice(PP.get(0), gpu, deviceCreateInfo);

            vkGetDeviceQueue(device, computeQueueFamily, 0, PP);
            computeQueue = new VkQueue(PP.get(0), device);

            CubicChunks.LOGGER.info("Logical device and compute queue created");
        }
    }

    private static void createVmaAllocator() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VmaVulkanFunctions vkFunctions = VmaVulkanFunctions.calloc(stack)
                .set(instance, device);

            VmaAllocatorCreateInfo allocatorCreateInfo = VmaAllocatorCreateInfo.calloc(stack)
                .physicalDevice(gpu)
                .device(device)
                .instance(instance)
                .vulkanApiVersion(VK_API_VERSION_1_2)
                .pVulkanFunctions(vkFunctions);

            check(vmaCreateAllocator(allocatorCreateInfo, PP));
            vmaAllocator = PP.get(0);

            CubicChunks.LOGGER.info("VMA allocator created");
        }
    }

    static void check(int errcode) {
        if (errcode != 0) {
            throw new IllegalStateException(String.format("Vulkan error [0x%X]", errcode));
        }
    }
}

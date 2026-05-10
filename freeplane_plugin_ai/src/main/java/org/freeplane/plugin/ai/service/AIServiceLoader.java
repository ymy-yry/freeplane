package org.freeplane.plugin.ai.service;

import org.freeplane.core.util.LogUtils;

import java.util.*;

/**
 * AI service loader.
 * Responsible for discovering and managing AI service providers.
 * Supports smart routing and Auto mode.
 */
public class AIServiceLoader {
    private static final Map<AIServiceType, List<AIService>> servicesByType = new HashMap<>();
    private static final Map<String, AIService> servicesByName = new HashMap<>();
    private static boolean initialized = false;
    private static final UserPreferenceConfig userPrefs = UserPreferenceConfig.getInstance();
    private static final PerformanceMonitor performanceMonitor = PerformanceMonitor.getInstance();

    /**
     * Initializes the service loader.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        // use ServiceLoader to load all AIService implementations
        ServiceLoader<AIService> loader = ServiceLoader.load(AIService.class);
        for (AIService service : loader) {
            registerService(service);
        }

        // if none found via ServiceLoader, register default services
        if (servicesByType.isEmpty()) {
            registerDefaultServices();
        }

        initialized = true;
        LogUtils.info("AIServiceLoader: initialized with " + servicesByName.size() + " services");
    }

    /**
     * Registers default services.
     */
    private static void registerDefaultServices() {
        try {
            // register the default chat service
            Class<?> chatServiceClass = Class.forName("org.freeplane.plugin.ai.service.impl.DefaultChatService");
            AIService chatService = (AIService) chatServiceClass.getDeclaredConstructor().newInstance();
            registerService(chatService);

            // register the default agent service
            Class<?> agentServiceClass = Class.forName("org.freeplane.plugin.ai.service.impl.DefaultAgentService");
            AIService agentService = (AIService) agentServiceClass.getDeclaredConstructor().newInstance();
            registerService(agentService);

        } catch (Exception e) {
            LogUtils.warn("AIServiceLoader: failed to register default services", e);
        }
    }

    /**
     * Registers a service.
     * @param service service instance
     */
    public static void registerService(AIService service) {
        AIServiceType type = service.getServiceType();
        String name = service.getServiceName();

        // group by type
        servicesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(service);
        // index by name
        servicesByName.put(name, service);

        LogUtils.info("AIServiceLoader: registered service - " + name + " (" + type.getCode() + ")");
    }

    /**
     * Returns all services of the specified type.
     * @param type service type
     * @return list of services
     */
    public static List<AIService> getServicesByType(AIServiceType type) {
        initialize();
        List<AIService> services = servicesByType.get(type);
        return services != null ? services : Collections.emptyList();
    }

    /**
     * Returns the service with the specified name.
     * @param name service name
     * @return service instance
     */
    public static AIService getServiceByName(String name) {
        initialize();
        return servicesByName.get(name);
    }

    /**
     * Selects the appropriate service based on the request.
     * Supports three modes:
     * 1. auto mode - automatically selects the best service based on task type
     * 2. specified serviceType - selects a service by the given type
     * 3. default preference - selects based on the user's default preference setting
     *
     * @param request request parameters
     * @return service instance
     */
    public static AIService selectService(Map<String, Object> request) {
        initialize();

        String serviceType = (String) request.get("serviceType");
        String model = (String) request.get("model");
        String taskType = (String) request.get("action");

        // Auto mode: select the best service based on task type and performance
        if (isAutoMode(serviceType)) {
            return selectServiceAuto(request, taskType);
        }

        // specific model requested
        if (model != null && !model.isEmpty()) {
            AIService service = getServiceByModel(model);
            if (service != null && service.canHandle(request)) {
                return service;
            }
        }

        // select by serviceType
        if (serviceType != null && !serviceType.isEmpty()) {
            AIServiceType type = AIServiceType.fromCode(serviceType);
            if (type != null) {
                List<AIService> services = getServicesByType(type);
                AIService selectedService = selectBestServiceByPriority(services, request);
                if (selectedService != null) {
                    return selectedService;
                }
            }
        }

        // select based on user default preference
        String defaultServiceType = userPrefs.getDefaultServiceType();
        if (defaultServiceType != null && !defaultServiceType.isEmpty()) {
            if (isAutoMode(defaultServiceType)) {
                return selectServiceAuto(request, taskType);
            }

            AIServiceType type = AIServiceType.fromCode(defaultServiceType);
            if (type != null) {
                List<AIService> services = getServicesByType(type);
                AIService selectedService = selectBestServiceByPriority(services, request);
                if (selectedService != null) {
                    return selectedService;
                }
            }
        }

        // iterate all services to find a suitable one
        for (List<AIService> services : servicesByType.values()) {
            for (AIService service : services) {
                if (service.canHandle(request)) {
                    return service;
                }
            }
        }

        return null;
    }

    /**
     * Returns whether the mode is Auto mode.
     */
    private static boolean isAutoMode(String serviceType) {
        return serviceType == null || serviceType.isEmpty() ||
               UserPreferenceConfig.SERVICE_TYPE_AUTO.equalsIgnoreCase(serviceType) ||
               "auto".equalsIgnoreCase(serviceType);
    }

    /**
     * Auto mode: intelligently selects the best service.
     */
    private static AIService selectServiceAuto(Map<String, Object> request, String taskType) {
        LogUtils.info("AIServiceLoader: Auto mode selecting service for task: " + taskType);

        // infer service type from task type
        AIServiceType inferredType = inferServiceType(taskType);
        List<AIService> candidateServices = getServicesByType(inferredType);

        if (candidateServices.isEmpty()) {
            // if no services for inferred type, try all types
            candidateServices = new ArrayList<>(servicesByName.values());
        }

        // sort by priority and select
        AIService selectedService = selectBestServiceByPriority(candidateServices, request);

        if (selectedService != null) {
            LogUtils.info("AIServiceLoader: Auto selected service: " + selectedService.getServiceName());
        }

        return selectedService;
    }

    /**
     * Infers the service type from the task type.
     */
    private static AIServiceType inferServiceType(String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            return AIServiceType.CHAT;
        }

        // agent tasks
        if (isAgentTask(taskType)) {
            return AIServiceType.AGENT;
        }

        // chat tasks
        if (isChatTask(taskType)) {
            return AIServiceType.CHAT;
        }

        // default to CHAT
        return AIServiceType.CHAT;
    }

    /**
     * Returns whether the task type is an agent task.
     */
    private static boolean isAgentTask(String taskType) {
        if (taskType == null) return false;

        String[] agentTasks = {
            "generate-mindmap", "expand-node", "summarize", "tag",
            "generate", "analyze", "plan", "execute"
        };

        for (String agentTask : agentTasks) {
            if (taskType.toLowerCase().contains(agentTask.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the task type is a chat task.
     */
    private static boolean isChatTask(String taskType) {
        if (taskType == null) return false;

        String[] chatTasks = {
            "chat", "message", "question", "answer", "talk"
        };

        for (String chatTask : chatTasks) {
            if (taskType.toLowerCase().contains(chatTask.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selects the best service by priority and performance.
     */
    private static AIService selectBestServiceByPriority(List<AIService> services, Map<String, Object> request) {
        if (services == null || services.isEmpty()) {
            return null;
        }

        if (services.size() == 1) {
            return services.get(0);
        }

        AIService bestService = null;
        int highestPriority = -1;

        for (AIService service : services) {
            if (!service.canHandle(request)) {
                continue;
            }

            int priority = service.getPriority();

            // if the user has set a model priority, adjust the service priority accordingly
            String modelKey = getModelKeyFromService(service);
            if (modelKey != null) {
                Integer userPriority = userPrefs.getModelPriorities().get(modelKey);
                if (userPriority != null) {
                    priority = userPriority;
                }
            }

            if (priority > highestPriority) {
                highestPriority = priority;
                bestService = service;
            }
        }

        return bestService;
    }

    /**
     * Returns the service for the given model name.
     */
    private static AIService getServiceByModel(String model) {
        for (AIService service : servicesByName.values()) {
            if (service instanceof ModelAwareService) {
                ModelAwareService awareService = (ModelAwareService) service;
                if (awareService.supportsModel(model)) {
                    return service;
                }
            }
        }
        return null;
    }

    /**
     * Returns the model key from the given service.
     */
    private static String getModelKeyFromService(AIService service) {
        if (service instanceof ModelAwareService) {
            return ((ModelAwareService) service).getDefaultModel();
        }
        return null;
    }

    /**
     * Returns the user preference configuration.
     */
    public static UserPreferenceConfig getUserPreferences() {
        return userPrefs;
    }

    /**
     * Returns the performance monitor.
     */
    public static PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    /**
     * Returns the smart routing report.
     */
    public static Map<String, Object> getRoutingReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("userPreferences", userPrefs.getAllPreferences());
        report.put("performanceReport", performanceMonitor.getPerformanceReport());
        report.put("availableServices", getAllServices().stream()
            .map(s -> Map.of(
                "name", s.getServiceName(),
                "type", s.getServiceType().getCode(),
                "priority", s.getPriority()
            )).toList());
        return report;
    }

    /**
     * Returns all registered services.
     * @return collection of services
     */
    public static Collection<AIService> getAllServices() {
        initialize();
        return servicesByName.values();
    }

    /**
     * Interface for services that are aware of which model they use.
     */
    public interface ModelAwareService {
        boolean supportsModel(String model);
        String getDefaultModel();
    }
}
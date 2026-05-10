package org.freeplane.plugin.ai.service.scheduling;

import org.freeplane.core.util.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Scheduling configuration - manages scheduler parameters loaded from a properties file.
 */
public class SchedulingConfig {
    // path to the config resource file
    private static final String CONFIG_FILE = "org/freeplane/plugin/ai/service/scheduling/scheduling.properties";

    // default parameter values
    private static final double DEFAULT_TEMPERATURE = 0.2;
    private static final double DEFAULT_TOP_P = 0.9;
    private static final Long DEFAULT_SEED = null;
    private static final Integer DEFAULT_TOP_K = null;
    private static final double DEFAULT_PRESENCE_PENALTY = 0.0;
    private static final double DEFAULT_FREQUENCY_PENALTY = 0.0;

    // configuration parameters
    private double temperature;
    private double topP;
    private Long seed;
    private Integer topK;
    private double presencePenalty;
    private double frequencyPenalty;
    private boolean enabled;

    // singleton instance
    private static volatile SchedulingConfig instance;

    /**
     * Private constructor - loads configuration.
     */
    private SchedulingConfig() {
        loadConfig();
    }

    /**
     * Returns the singleton configuration instance.
     * @return configuration instance
     */
    public static synchronized SchedulingConfig getInstance() {
        if (instance == null) {
            instance = new SchedulingConfig();
        }
        return instance;
    }

    /**
     * Resets the singleton instance (useful for testing).
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    /**
     * Loads configuration from the properties file.
     */
    private void loadConfig() {
        Properties props = new Properties();
        try {
            InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
            if (input != null) {
                props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                LogUtils.info("SchedulingConfig: loaded from " + CONFIG_FILE);
            } else {
                LogUtils.warn("SchedulingConfig: config file not found, using defaults");
            }
        } catch (IOException e) {
            LogUtils.warn("SchedulingConfig: failed to load config", e);
        }

        temperature = getDoubleProperty(props, "temperature", DEFAULT_TEMPERATURE);
        topP = getDoubleProperty(props, "topP", DEFAULT_TOP_P);
        seed = getLongProperty(props, "seed", DEFAULT_SEED);
        topK = getIntegerProperty(props, "topK", DEFAULT_TOP_K);
        presencePenalty = getDoubleProperty(props, "presencePenalty", DEFAULT_PRESENCE_PENALTY);
        frequencyPenalty = getDoubleProperty(props, "frequencyPenalty", DEFAULT_FREQUENCY_PENALTY);
        enabled = getBooleanProperty(props, "enabled", true);

        LogUtils.info("SchedulingConfig: config loaded - temperature: " + temperature + ", topP: " + topP);
    }

    private double getDoubleProperty(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                LogUtils.warn("SchedulingConfig: invalid value for " + key + ", using default", e);
            }
        }
        return defaultValue;
    }

    private Long getLongProperty(Properties props, String key, Long defaultValue) {
        String value = props.getProperty(key);
        if (value != null && !value.equals("null")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                LogUtils.warn("SchedulingConfig: invalid value for " + key + ", using default", e);
            }
        }
        return defaultValue;
    }

    private Integer getIntegerProperty(Properties props, String key, Integer defaultValue) {
        String value = props.getProperty(key);
        if (value != null && !value.equals("null")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LogUtils.warn("SchedulingConfig: invalid value for " + key + ", using default", e);
            }
        }
        return defaultValue;
    }

    private boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    // Getters and setters
    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        if (temperature >= 0.0 && temperature <= 2.0) {
            this.temperature = temperature;
        } else {
            LogUtils.warn("SchedulingConfig: temperature must be between 0.0 and 2.0, keeping current value: " + this.temperature);
        }
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        if (topP >= 0.0 && topP <= 1.0) {
            this.topP = topP;
        } else {
            LogUtils.warn("SchedulingConfig: topP must be between 0.0 and 1.0, keeping current value: " + this.topP);
        }
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        if (topK == null || topK > 0) {
            this.topK = topK;
        } else {
            LogUtils.warn("SchedulingConfig: topK must be a positive integer or null, keeping current value: " + this.topK);
        }
    }

    public double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(double presencePenalty) {
        if (presencePenalty >= -2.0 && presencePenalty <= 2.0) {
            this.presencePenalty = presencePenalty;
        } else {
            LogUtils.warn("SchedulingConfig: presencePenalty must be between -2.0 and 2.0, keeping current value: " + this.presencePenalty);
        }
    }

    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(double frequencyPenalty) {
        if (frequencyPenalty >= -2.0 && frequencyPenalty <= 2.0) {
            this.frequencyPenalty = frequencyPenalty;
        } else {
            LogUtils.warn("SchedulingConfig: frequencyPenalty must be between -2.0 and 2.0, keeping current value: " + this.frequencyPenalty);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void reloadConfig() {
        loadConfig();
    }

    @Override
    public String toString() {
        return "SchedulingConfig{" +
                "temperature=" + temperature +
                ", topP=" + topP +
                ", seed=" + seed +
                ", topK=" + topK +
                ", presencePenalty=" + presencePenalty +
                ", frequencyPenalty=" + frequencyPenalty +
                ", enabled=" + enabled +
                '}';
    }
}

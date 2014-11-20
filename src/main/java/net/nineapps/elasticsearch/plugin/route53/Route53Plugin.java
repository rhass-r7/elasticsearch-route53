package net.nineapps.elasticsearch.plugin.route53;

import static org.elasticsearch.common.collect.Lists.newArrayList;

import java.util.Collection;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.AbstractPlugin;

public class Route53Plugin extends AbstractPlugin {

    private final Settings settings;

    public Route53Plugin(Settings settings) {
        this.settings = settings;
    }

    public String name() {
        return "route53-plugin";
  }

  public String description() {
        return "Plugin which registers the current Elasticsearch node into Route53";
  }

    @Override
    public Collection<Class<? extends LifecycleComponent>> services() {
    Collection<Class<? extends LifecycleComponent>> services = newArrayList();
        if (settings.getAsBoolean("route53.registration.enabled", true)) {
            services.add(Route53PluginService.class);
        }
        return services;
    }}

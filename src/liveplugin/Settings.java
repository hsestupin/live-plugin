/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package liveplugin;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@State(name = "LivePluginSettings", storages = {@Storage(id = "other", file = "$APP_CONFIG$/live-plugin.xml")})
public class Settings implements PersistentStateComponent<Settings> {
	public boolean justInstalled = true;
	public boolean runAllPluginsOnIDEStartup = false;
	public Map<String, Integer> pluginsUsage = new HashMap<>();

	public static Settings getInstance() {
		return ServiceManager.getService(Settings.class);
	}

	@Nullable @Override public Settings getState() {
		return this;
	}

	@Override public void loadState(Settings settings) {
		XmlSerializerUtil.copyBean(settings, this);
	}

	public static void countPluginsUsage(Collection<String> pluginIds) {
		Map<String, Integer> pluginsUsage = Settings.getInstance().pluginsUsage;
		for (String pluginId : pluginIds) {
            Integer count = pluginsUsage.get(pluginId);
            if (count == null) count = 0;
            pluginsUsage.put(pluginId, count + 1);
        }
	}

}

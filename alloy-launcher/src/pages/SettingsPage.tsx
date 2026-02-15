import { useState, useEffect } from "react";
import { useStore } from "../lib/store";
import Card from "../components/ui/Card";
import Input from "../components/ui/Input";
import Button from "../components/ui/Button";

export default function SettingsPage() {
  const settings = useStore((s) => s.settings);
  const updateSettings = useStore((s) => s.updateSettings);
  const profile = useStore((s) => s.profile);
  const logout = useStore((s) => s.logout);

  const [memory, setMemory] = useState(settings.memory_mb);
  const [javaPath, setJavaPath] = useState(settings.java_path || "");
  const [jvmArgs, setJvmArgs] = useState(settings.jvm_args);
  const [clientId, setClientId] = useState(settings.client_id);

  useEffect(() => {
    setMemory(settings.memory_mb);
    setJavaPath(settings.java_path || "");
    setJvmArgs(settings.jvm_args);
    setClientId(settings.client_id);
  }, [settings]);

  const save = () => {
    updateSettings({
      ...settings,
      memory_mb: memory,
      java_path: javaPath || null,
      jvm_args: jvmArgs,
      client_id: clientId,
    });
  };

  return (
    <div className="space-y-6 p-6">
      <h1 className="font-heading text-xl font-semibold text-stone-100">
        Settings
      </h1>

      {/* Account */}
      <Card className="p-5">
        <h2 className="mb-4 font-heading text-sm font-semibold uppercase tracking-wider text-stone-400">
          Account
        </h2>
        {profile ? (
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <img
                src={`https://mc-heads.net/head/${profile.uuid.replaceAll("-", "")}/56`}
                alt={profile.username}
                className="h-14 w-14 rounded-lg"
              />
              <div>
                <p className="font-heading font-semibold text-stone-100">
                  {profile.username}
                </p>
                <p className="font-mono text-xs text-stone-500">
                  {profile.uuid}
                </p>
              </div>
            </div>
            <Button variant="danger" onClick={logout}>
              Log out
            </Button>
          </div>
        ) : (
          <p className="text-sm text-stone-400">Not signed in</p>
        )}
      </Card>

      {/* Game */}
      <Card className="space-y-5 p-5">
        <h2 className="font-heading text-sm font-semibold uppercase tracking-wider text-stone-400">
          Game
        </h2>

        {/* Memory slider */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-xs font-medium text-stone-400">
              Memory
            </label>
            <span className="font-mono text-xs text-ember">
              {(memory / 1024).toFixed(1)} GB
            </span>
          </div>
          <input
            type="range"
            min={1024}
            max={16384}
            step={512}
            value={memory}
            onChange={(e) => setMemory(Number(e.target.value))}
            className="w-full accent-ember"
          />
          <div className="flex justify-between text-[10px] text-stone-500">
            <span>1 GB</span>
            <span>16 GB</span>
          </div>
        </div>

        <Input
          label="Java Path"
          placeholder="Auto-detect"
          value={javaPath}
          onChange={(e) => setJavaPath(e.target.value)}
        />

        <Input
          label="Custom JVM Arguments"
          placeholder="-XX:+UseG1GC"
          value={jvmArgs}
          onChange={(e) => setJvmArgs(e.target.value)}
        />
      </Card>

      {/* Launcher */}
      <Card className="space-y-5 p-5">
        <h2 className="font-heading text-sm font-semibold uppercase tracking-wider text-stone-400">
          Launcher
        </h2>

        <Input
          label="Azure Client ID"
          placeholder="95ae4c3a-16c9-4a43-9f5c-139ae91fff9a"
          value={clientId}
          onChange={(e) => setClientId(e.target.value)}
        />

        <div className="text-xs text-stone-500">
          <p>
            The default Client ID is registered by the Alloy team. Developers
            can register their own at{" "}
            <span className="text-ember">portal.azure.com</span>.
          </p>
        </div>
      </Card>

      {/* Save */}
      <div className="flex justify-end">
        <Button onClick={save}>Save Settings</Button>
      </div>

      {/* About */}
      <div className="text-center text-xs text-stone-500">
        Alloy Launcher v0.1.0 &middot; alloymc.net
      </div>
    </div>
  );
}

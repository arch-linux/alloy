import { useState } from "react";
import Card from "../ui/Card";
import type { MinecraftProfile } from "../../lib/types";

function avatarUrl(uuid: string): string {
  const clean = uuid.replaceAll("-", "");
  return `https://mc-heads.net/head/${clean}/64`;
}

export default function PlayerCard({ profile }: { profile: MinecraftProfile }) {
  const [imgError, setImgError] = useState(false);

  return (
    <Card glow className="flex items-center gap-4 p-4">
      {imgError ? (
        <div className="flex h-16 w-16 items-center justify-center rounded-lg bg-obsidian-700 text-2xl font-heading font-bold text-ember">
          {profile.username[0]?.toUpperCase()}
        </div>
      ) : (
        <img
          src={avatarUrl(profile.uuid)}
          alt={profile.username}
          className="h-16 w-16 rounded-lg"
          onError={() => setImgError(true)}
        />
      )}
      <div>
        <h2 className="font-heading text-lg font-semibold text-stone-100">
          {profile.username}
        </h2>
        <p className="text-sm text-stone-400">Ready to play</p>
      </div>
    </Card>
  );
}

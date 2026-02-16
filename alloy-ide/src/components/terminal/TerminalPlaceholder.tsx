export default function TerminalPlaceholder() {
  return (
    <div
      className="h-full w-full p-3 font-mono text-xs"
      style={{
        background: "linear-gradient(180deg, #111118 0%, #0a0a10 100%)",
      }}
    >
      <div className="flex items-center gap-1 text-stone-500">
        <span className="text-ember">alloy</span>
        <span className="text-stone-500">@</span>
        <span className="text-forge-gold">forge</span>
        <span className="text-stone-500 mx-1">~</span>
        <span className="text-stone-400">$</span>
        <span className="ml-1 inline-block w-[7px] h-[14px] bg-ember animate-pulse" />
      </div>
      <div className="mt-4 text-stone-500 text-center">
        Terminal integration coming soon
      </div>
    </div>
  );
}

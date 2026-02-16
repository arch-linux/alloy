export default function EmberGlow() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden">
      <div
        className="absolute animate-glow-pulse"
        style={{
          width: "60%",
          height: "60%",
          left: "20%",
          top: "30%",
          background:
            "radial-gradient(ellipse at center, rgba(255, 107, 0, 0.08) 0%, transparent 70%)",
          filter: "blur(60px)",
        }}
      />
      <div
        className="absolute animate-glow-pulse"
        style={{
          width: "40%",
          height: "40%",
          left: "30%",
          top: "50%",
          background:
            "radial-gradient(ellipse at center, rgba(240, 184, 48, 0.05) 0%, transparent 70%)",
          filter: "blur(40px)",
          animationDelay: "2s",
        }}
      />
    </div>
  );
}

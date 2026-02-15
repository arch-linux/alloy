import { useMemo } from "react";

interface Spark {
  id: number;
  left: string;
  bottom: string;
  size: number;
  delay: string;
  duration: string;
  opacity: number;
}

export default function ForgeSparks({ count = 20 }: { count?: number }) {
  const sparks = useMemo<Spark[]>(() => {
    return Array.from({ length: count }, (_, i) => ({
      id: i,
      left: `${Math.random() * 100}%`,
      bottom: `${Math.random() * 30}%`,
      size: 2 + Math.random() * 3,
      delay: `${Math.random() * 4}s`,
      duration: `${1.5 + Math.random() * 2}s`,
      opacity: 0.4 + Math.random() * 0.6,
    }));
  }, [count]);

  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden">
      {sparks.map((spark) => (
        <div
          key={spark.id}
          className="absolute rounded-full animate-spark-rise"
          style={{
            left: spark.left,
            bottom: spark.bottom,
            width: spark.size,
            height: spark.size,
            backgroundColor: `rgba(255, ${100 + Math.floor(Math.random() * 80)}, 0, ${spark.opacity})`,
            animationDelay: spark.delay,
            animationDuration: spark.duration,
            boxShadow: `0 0 ${spark.size * 2}px rgba(255, 107, 0, ${spark.opacity * 0.5})`,
          }}
        />
      ))}
    </div>
  );
}

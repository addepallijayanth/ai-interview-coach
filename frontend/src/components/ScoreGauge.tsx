import { useEffect, useState } from 'react';
import { scoreTone } from '../lib/format';

interface Props {
  score: number;
  max?: number;
  size?: number;
  label?: string;
}

/** Circular score ring that animates from zero on first render. */
export function ScoreGauge({ score, max = 100, size = 152, label = 'Match' }: Props) {
  const stroke = 12;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const [shown, setShown] = useState(0);

  // A timer rather than requestAnimationFrame: rAF never fires in background tabs, which would leave the ring empty.
  useEffect(() => {
    const timer = setTimeout(() => setShown(score), 30);
    return () => clearTimeout(timer);
  }, [score]);

  const offset = circumference * (1 - Math.min(Math.max(shown, 0), max) / max);
  return (
    <div className="gauge" role="img" aria-label={`${label} score: ${score} out of ${max}`}>
      <svg width={size} height={size}>
        <circle className="track" cx={size / 2} cy={size / 2} r={radius} fill="none" strokeWidth={stroke} />
        <circle
          className={`value ${scoreTone(score, max)}`}
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
        />
      </svg>
      <div className="gauge-label">
        <div className="number">{score}</div>
        <div className="caption">{label}</div>
      </div>
    </div>
  );
}

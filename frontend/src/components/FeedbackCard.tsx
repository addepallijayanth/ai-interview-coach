import type { AnswerFeedback } from '../api/types';
import { scoreTone } from '../lib/format';

export function FeedbackCard({ score, feedback }: { score: number; feedback?: AnswerFeedback }) {
  const tone = scoreTone(score, 10);
  return (
    <div className={`feedback ${tone}`}>
      <div className="row">
        <span className={`badge ${tone}`}>{score}/10</span>
        <span className="small muted">Feedback on your answer</span>
      </div>
      {feedback && (
        <>
          <ul>
            {feedback.strengths.map((item) => <li key={`+${item}`} className="plus">{item}</li>)}
            {feedback.weaknesses.map((item) => <li key={`-${item}`} className="minus">{item}</li>)}
          </ul>
          <details>
            <summary>Show a strong answer</summary>
            <p>{feedback.modelAnswer}</p>
          </details>
        </>
      )}
    </div>
  );
}

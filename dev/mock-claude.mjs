#!/usr/bin/env node
/**
 * A tiny stand-in for the Claude Messages API, for trying the app locally without an API key.
 *
 *   node dev/mock-claude.mjs            # listens on http://localhost:8089
 *   ANTHROPIC_BASE_URL=http://localhost:8089 ANTHROPIC_API_KEY=mock ./mvnw spring-boot:test-run
 *
 * It answers in the real wire format (JSON and server-sent events), returns schema-valid structured output
 * built from simple keyword matching, and plays the interviewer by following the same tool protocol the real
 * model does (profile/requirements tools, score_answer after each answer, end_interview at the end).
 * Responses are canned: this is for demos and UI work, not a model.
 */
import http from 'node:http';

const PORT = Number(process.env.MOCK_CLAUDE_PORT ?? 8089);
const SKILLS = ['Java', 'Spring Boot', 'Kotlin', 'Python', 'Django', 'Go', 'JavaScript', 'TypeScript', 'React',
  'Node.js', 'SQL', 'PostgreSQL', 'MySQL', 'MongoDB', 'Redis', 'Kafka', 'RabbitMQ', 'Docker', 'Kubernetes', 'AWS',
  'Azure', 'GCP', 'Terraform', 'CI/CD', 'GraphQL', 'REST', 'gRPC', 'Microservices', 'Spark', 'Airflow',
  'Machine Learning', 'Linux', 'Git', 'Agile'];
// Canned questions per category, used in turn so a long interview rarely repeats itself. {skill} is filled with
// one of the candidate's skill gaps or matched skills, {role} with the job title.
const QUESTIONS = {
  'Technical': [
    "Let's talk about {skill}. What problems does it solve well, and when would you avoid it?",
    'How would you debug a {skill} issue that only shows up in production under load?',
    'What are the most common mistakes teams make with {skill}, and how do you guard against them?',
    'How would you make a {skill}-based component easy to test and maintain? What would you test first?',
    'Explain a {skill} concept you think most developers misunderstand, as you would to a new teammate.',
  ],
  'Problem solving': [
    'Given a stream of millions of events, how would you find the 10 most frequent user IDs? Walk me through your approach and its time and space complexity.',
    'How would you detect a cycle in a dependency graph, for example between services? Explain the algorithm and its complexity.',
    'Design an LRU cache with O(1) get and put. Which data structures would you combine, and why?',
    'How would you merge overlapping time ranges from a large list of calendar bookings? What is the complexity?',
  ],
  'System design': [
    "Design a URL shortener that handles 10,000 requests per second. Cover the API, the data model, scaling and how you'd avoid collisions.",
    'How would you design a notification service that sends email, SMS and push messages reliably and never sends duplicates?',
    'Design a rate limiter for a public API. Where would it live, which algorithm would you use, and how would it work across several servers?',
    "How would you design the backend for a ride-sharing app's live driver tracking?",
  ],
  'Project deep-dive': [
    "Walk me through the project on your resume you're proudest of. What was your exact role, and what was the hardest decision you made?",
    'Pick a project that did not go to plan. What happened, how did you handle it, and what would you do differently today?',
    'Tell me about a project where you used {skill}. Why was it the right choice, and what result did it deliver?',
    'Which project on your resume had the biggest measurable impact, and how did you measure it?',
  ],
  'Behavioural': [
    'Tell me about a time you disagreed with a teammate on a technical decision. What did you do, and what was the outcome?',
    'Describe a production incident you were involved in. How did you find the cause, and what did you change afterwards?',
    'Tell me about a time you had to learn something new very quickly to deliver on time.',
    'Describe a time you received tough feedback. How did you respond, and what changed?',
    'Tell me about a time you took ownership of a problem that was not strictly yours.',
  ],
  'Situational': [
    "It's Friday evening and a release you shipped is causing errors for 5% of users. What do you do in the first 30 minutes?",
    "Your team is two weeks from a deadline and you realise the scope won't fit. How do you handle it with your manager and stakeholders?",
    'A senior colleague insists on an approach you believe is wrong. What would you do?',
    'You join a team with almost no tests and frequent outages. What would you change first, and why?',
  ],
  'HR': [
    'What are your career goals for the next three years, and how does this role fit into them?',
    "What's your biggest strength, and one area you're actively working to improve?",
    'What kind of team culture and work style help you do your best work?',
  ],
};
let ids = 0;

http.createServer((req, res) => {
  if (req.method !== 'POST' || !req.url.startsWith('/v1/messages')) {
    res.writeHead(404).end();
    return;
  }
  let body = '';
  req.on('data', (chunk) => (body += chunk));
  req.on('end', () => {
    const request = JSON.parse(body);
    try {
      if (request.tools) return interview(request, res);
      return structured(request, res);
    } catch (error) {
      console.error(error);
      res.writeHead(500, { 'Content-Type': 'application/json' })
        .end(JSON.stringify({ type: 'error', error: { type: 'api_error', message: String(error) } }));
    }
  });
}).listen(PORT, () => console.log(`Mock Claude API listening on http://localhost:${PORT}`));

// ---------------------------------------------------------------- structured output (analysis, study plan)

function structured(request, res) {
  const schema = request.output_config?.format?.schema?.properties ?? {};
  const prompt = textOf(request.messages.at(-1));
  let result;
  if (schema.skills) result = resumeProfile(tagged(prompt, 'resume'));
  else if (schema.requiredSkills) result = jobRequirements(tagged(prompt, 'job_description'));
  else if (schema.matchScore) result = match(tagged(prompt, 'resume'), tagged(prompt, 'job_description'));
  else if (schema.focusAreas) result = studyPlan(tagged(prompt, 'missing_skills'));
  else throw new Error('Unknown structured-output schema');
  console.log(`structured ${Object.keys(schema)[0]} (${request.model})`);
  setTimeout(() => sendJson(res, request.model, JSON.stringify(result)), 600);
}

function skillsIn(text) {
  const lower = ` ${text.toLowerCase()} `;
  return SKILLS.filter((skill) => new RegExp(`[^a-z]${escape(skill.toLowerCase())}[^a-z]`).test(lower));
}

function resumeProfile(resume) {
  const years = [...resume.matchAll(/\b(19|20)\d{2}\b/g)].map((m) => Number(m[0]));
  if (/present|current/i.test(resume)) years.push(new Date().getFullYear());
  return {
    skills: skillsIn(resume),
    yearsExperience: years.length >= 2 ? Math.max(1, Math.max(...years) - Math.min(...years)) : 3,
    roles: resume.split('\n').filter((l) => /engineer|developer|analyst|manager|designer/i.test(l) && l.length < 90).map((l) => l.split(/[,(]/)[0].trim()).slice(0, 3),
    seniority: /senior|lead/i.test(resume) ? 'senior' : 'mid',
    education: (resume.match(/(B\.?Tech|B\.?E\.?|B\.?Sc|M\.?Sc|MBA|Bachelor|Master)[^\n]*/i) ?? [''])[0].slice(0, 60),
    highlights: bullets(resume).slice(0, 3),
  };
}

function jobRequirements(job) {
  const lines = job.split('\n').map((l) => l.trim()).filter(Boolean);
  const [title, company] = (lines[0] ?? 'Software Engineer').split(/\s+at\s+/i);
  const niceIndex = job.toLowerCase().indexOf('nice to have');
  const required = skillsIn(niceIndex > 0 ? job.slice(0, niceIndex) : job);
  const nice = niceIndex > 0 ? skillsIn(job.slice(niceIndex)).filter((s) => !required.includes(s)) : [];
  return {
    title: title.slice(0, 80), company: (company ?? '').slice(0, 60), seniority: /senior|staff|lead/i.test(job) ? 'senior' : 'mid',
    yearsRequired: Number((job.match(/(\d+)\+?\s*years/i) ?? [0, 0])[1]),
    requiredSkills: required.length ? required : ['Communication'], niceToHaveSkills: nice,
    responsibilities: lines.filter((l) => /^(you will|own|build|design|lead|mentor)/i.test(l)).slice(0, 4),
  };
}

function match(resume, job) {
  const have = new Set(skillsIn(resume));
  const required = jobRequirements(job).requiredSkills;
  const matched = required.filter((s) => have.has(s));
  const missing = required.filter((s) => !have.has(s));
  const score = Math.round(Math.min(95, 20 + (75 * matched.length) / Math.max(1, required.length)));
  return {
    matchScore: score, matchedSkills: matched, missingSkills: missing,
    strengths: matched.slice(0, 3).map((s) => `Hands-on ${s} experience`),
    suggestions: bullets(resume).slice(0, 3).map((original) => ({
      original,
      improved: `${original.replace(/\.$/, '')}, delivering [X%] measurable impact for the business`,
      reason: `Quantified outcomes make the bullet more persuasive for this ${required[0] ?? ''} role.`,
    })),
    summary: missing.length
      ? `You cover ${matched.length} of ${required.length} required skills. Closing the gap on ${missing.slice(0, 2).join(' and ')} would make you a much stronger candidate.`
      : 'You cover every required skill. Focus your preparation on depth and concrete examples.',
  };
}

function studyPlan(missing) {
  const topics = missing.split(',').map((s) => s.trim()).filter(Boolean).slice(0, 3);
  const areas = (topics.length ? topics : ['System design']).map((topic, index) => ({
    topic, priority: index === 0 ? 'high' : 'medium',
    why: `${topic} is required for the role and was not evidenced in your resume or answers.`,
    actions: [`Build a small project that uses ${topic} end to end`, `Prepare a two-minute explanation of core ${topic} concepts`],
    resources: [`Official ${topic} documentation`],
  }));
  return { summary: 'Focus on the required skills you have not yet demonstrated, then rehearse answers out loud.', focusAreas: areas };
}

// ---------------------------------------------------------------- the interviewer (streaming tool loop)

function interview(request, res) {
  const messages = request.messages;
  const last = messages.at(-1);
  // A question is an assistant turn with text and no tool call.
  const asked = messages.filter((m) => m.role === 'assistant' && !blocks(m).some((b) => b.type === 'tool_use')
    && blocks(m).some((b) => b.type === 'text' && b.text.trim())).length;
  const system = systemText(request);
  const plan = planFrom(system);
  const role = (system.match(/^Role: (.+)$/m) ?? [0, 'this'])[1];
  const gaps = gapsFrom(messages);

  if (last.role === 'system') return stream(res, request.model, [toolUse('end_interview', { summary: summary(messages, plan) })], 'tool_use');
  const results = blocks(last).filter((b) => b.type === 'tool_result');
  if (results.length) {
    const resultText = results.map((r) => (typeof r.content === 'string' ? r.content : JSON.stringify(r.content))).join(' ');
    if (/interview is complete|ended the interview/i.test(resultText)) {
      return stream(res, request.model, [toolUse('end_interview', { summary: summary(messages, plan) })], 'tool_use');
    }
    if (/has ended/i.test(resultText)) return stream(res, request.model, [{ type: 'text', text: 'Thanks for your time!' }], 'end_turn');
    return stream(res, request.model, [{ type: 'text', text: question(asked + 1, plan, gaps, role) }], 'end_turn');
  }
  const answer = textOf(last).match(/<candidate_answer question_no="(\d+)">([\s\S]*)<\/candidate_answer>/);
  if (answer) return stream(res, request.model, [toolUse('score_answer', scoreFor(Number(answer[1]), answer[2].trim()))], 'tool_use');
  // Kick-off: look up the profile and the job first, like the real agent is told to.
  return stream(res, request.model, [toolUse('get_candidate_profile', {}), toolUse('get_job_requirements', {})], 'tool_use');
}

/** The question plan from the session's system prompt: element 0 is the category of question 1. */
function planFrom(system) {
  const block = system.match(/Question plan \(question number: category\):\n([\s\S]*?)\n<\/interview_settings>/);
  return block ? block[1].split('\n').map((line) => line.replace(/^\d+:\s*/, '').trim()) : [];
}

function question(number, plan, gaps, role) {
  const category = plan[number - 1] ?? 'Technical';
  if (category === 'HR' && number === 1) {
    return `Thanks for joining! To start, tell me about yourself and what draws you to the ${role.split(' at ')[0]} role.`;
  }
  if (category === 'HR' && number === plan.length) {
    return "We're nearly done. Why do you want this role, and what would you bring to the team in your first six months?";
  }
  // Use the n-th question of this category, so repeats of a category get different questions.
  const occurrence = plan.slice(0, number - 1).filter((c) => c === category).length;
  const bank = QUESTIONS[category] ?? QUESTIONS.Technical;
  const skill = gaps[(number - 1) % Math.max(1, gaps.length)] ?? 'distributed systems';
  return bank[occurrence % bank.length].replaceAll('{skill}', skill);
}

function scoreFor(questionNo, answer) {
  const words = answer.split(/\s+/).filter(Boolean).length;
  const score = words < 8 ? 3 : words < 30 ? 6 : 8;
  return {
    question_no: questionNo, score,
    strengths: words >= 8 ? ['Answered the question directly'] : [],
    weaknesses: words < 30 ? ['Add a concrete example with numbers (situation, action, result)'] : ['Could mention trade-offs explicitly'],
    model_answer: 'A strong answer names a specific situation, explains the approach and the alternatives that were considered, and ends with a measurable result and what you learned.',
  };
}

function summary(messages, plan) {
  const scored = messages.flatMap(blocks).filter((b) => b.type === 'tool_use' && b.name === 'score_answer').map((b) => b.input);
  const scores = scored.map((s) => s.score);
  const average = scores.length ? (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1) : 'n/a';
  const byCategory = {};
  for (const { question_no: questionNo, score } of scored) {
    const category = plan[questionNo - 1] ?? 'Technical';
    (byCategory[category] ??= []).push(score);
  }
  const ranked = Object.entries(byCategory)
    .map(([category, list]) => [category, list.reduce((a, b) => a + b, 0) / list.length])
    .sort((a, b) => b[1] - a[1]);
  const strongest = ranked.length > 1 ? ` Your strongest area was ${ranked[0][0]}; focus next on ${ranked.at(-1)[0]}.` : '';
  return `You answered ${scores.length} question(s) with an average score of ${average}/10.${strongest} The biggest improvement is adding concrete, quantified examples and naming the trade-offs you considered.`;
}

function gapsFrom(messages) {
  for (const message of messages) {
    for (const block of blocks(message)) {
      if (block.type === 'tool_result' && typeof block.content === 'string' && block.content.includes('skillGaps')) {
        try {
          const profile = JSON.parse(block.content);
          return [...profile.skillGaps, ...profile.matchedSkills];
        } catch { /* not the profile */ }
      }
    }
  }
  return [];
}

// ---------------------------------------------------------------- wire format helpers

function toolUse(name, input) {
  return { type: 'tool_use', id: `toolu_mock_${++ids}`, name, input };
}

function sendJson(res, model, text) {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    id: `msg_mock_${++ids}`, type: 'message', role: 'assistant', model,
    content: [{ type: 'thinking', thinking: '', signature: `mock-${ids}` }, { type: 'text', text }],
    stop_reason: 'end_turn', stop_sequence: null,
    usage: { input_tokens: 900 + text.length, output_tokens: Math.round(text.length / 4), cache_creation_input_tokens: 0, cache_read_input_tokens: 0 },
  }));
}

async function stream(res, model, content, stopReason) {
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' });
  const send = (event, data) => res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  console.log(`interview -> ${content.map((b) => b.type === 'text' ? 'text' : b.name).join(', ')}`);
  send('message_start', { type: 'message_start', message: { id: `msg_mock_${++ids}`, type: 'message', role: 'assistant', model, content: [], stop_reason: null, stop_sequence: null, usage: { input_tokens: 2400, output_tokens: 1, cache_creation_input_tokens: 0, cache_read_input_tokens: 1800 } } });
  send('content_block_start', { type: 'content_block_start', index: 0, content_block: { type: 'thinking', thinking: '', signature: '' } });
  send('content_block_delta', { type: 'content_block_delta', index: 0, delta: { type: 'signature_delta', signature: `mock-sig-${ids}` } });
  send('content_block_stop', { type: 'content_block_stop', index: 0 });
  await wait(700);
  for (let i = 0; i < content.length; i++) {
    const block = content[i];
    const index = i + 1;
    if (block.type === 'text') {
      send('content_block_start', { type: 'content_block_start', index, content_block: { type: 'text', text: '' } });
      for (const piece of block.text.match(/\S+\s*/g) ?? []) {
        send('content_block_delta', { type: 'content_block_delta', index, delta: { type: 'text_delta', text: piece } });
        await wait(35);
      }
    } else {
      send('content_block_start', { type: 'content_block_start', index, content_block: { type: 'tool_use', id: block.id, name: block.name, input: {} } });
      send('content_block_delta', { type: 'content_block_delta', index, delta: { type: 'input_json_delta', partial_json: JSON.stringify(block.input) } });
      await wait(400);
    }
    send('content_block_stop', { type: 'content_block_stop', index });
  }
  send('message_delta', { type: 'message_delta', delta: { stop_reason: stopReason, stop_sequence: null }, usage: { input_tokens: 2400, output_tokens: 120, cache_creation_input_tokens: 0, cache_read_input_tokens: 1800 } });
  send('message_stop', { type: 'message_stop' });
  res.end();
}

function blocks(message) {
  if (!message) return [];
  return typeof message.content === 'string' ? [{ type: 'text', text: message.content }] : message.content;
}

function textOf(message) {
  return blocks(message).filter((b) => b.type === 'text').map((b) => b.text).join('\n');
}

function systemText(request) {
  return Array.isArray(request.system) ? request.system.map((b) => b.text).join('\n') : String(request.system ?? '');
}

function tagged(text, tag) {
  const found = text.match(new RegExp(`<${tag}>\\n?([\\s\\S]*?)\\n?</${tag}>`));
  return found ? found[1] : '';
}

function bullets(text) {
  return text.split('\n').map((l) => l.trim()).filter((l) => /^[-•*]\s+/.test(l)).map((l) => l.replace(/^[-•*]\s+/, ''));
}

function escape(value) {
  return value.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
}

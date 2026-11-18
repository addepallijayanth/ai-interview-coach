import { useState } from 'react';

const ACCEPT = '.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document';

export function FileDropzone({ onFile, disabled }: { onFile: (file: File) => void; disabled?: boolean }) {
  const [dragging, setDragging] = useState(false);
  return (
    <label
      className={`dropzone${dragging ? ' dragging' : ''}`}
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        const file = event.dataTransfer.files[0];
        if (file && !disabled) onFile(file);
      }}
    >
      <input
        type="file"
        accept={ACCEPT}
        disabled={disabled}
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) onFile(file);
          event.target.value = '';
        }}
      />
      <div><strong>Choose your resume</strong> or drag it here</div>
      <div className="small muted">PDF or DOCX, up to 2 MB</div>
    </label>
  );
}

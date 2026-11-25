import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  CheckCircle2,
  Film,
  ImagePlus,
  Loader2,
  Smartphone,
  UploadCloud,
  X,
} from 'lucide-react';
import { extractApiError, videosApi } from '../../services/api';
import { CATEGORIES, type VideoCardItem } from '../../types/api';
import { formatFileSize } from '../../utils/format';

interface UploadModalProps {
  open: boolean;
  onClose: () => void;
  onUploaded: (video: VideoCardItem, message: string) => void;
}

const UPLOAD_CATEGORIES = CATEGORIES.filter((c) => c.id !== 'all' && c.id !== 'recent');

export const UploadModal: React.FC<UploadModalProps> = ({ open, onClose, onUploaded }) => {
  const [videoFile, setVideoFile] = useState<File | null>(null);
  const [thumbFile, setThumbFile] = useState<File | null>(null);
  const [thumbPreviewUrl, setThumbPreviewUrl] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('tech');
  const [tags, setTags] = useState('');
  const [isShort, setIsShort] = useState(false);

  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadPct, setUploadPct] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const videoInputRef = useRef<HTMLInputElement | null>(null);
  const thumbInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (!thumbFile) {
      setThumbPreviewUrl(null);
      return;
    }
    const objUrl = URL.createObjectURL(thumbFile);
    setThumbPreviewUrl(objUrl);
    return () => URL.revokeObjectURL(objUrl);
  }, [thumbFile]);

  const resetForm = useCallback(() => {
    setVideoFile(null);
    setThumbFile(null);
    setThumbPreviewUrl(null);
    setTitle('');
    setDescription('');
    setCategory('tech');
    setTags('');
    setIsShort(false);
    setUploadPct(0);
    setError(null);
    setIsDragging(false);
  }, []);

  const handleClose = useCallback(() => {
    if (isUploading) return;
    resetForm();
    onClose();
  }, [isUploading, resetForm, onClose]);

  const handlePickVideoFile = useCallback((file: File | null) => {
    if (!file) return;
    const ext = file.name.split('.').pop()?.toLowerCase() || '';
    if (!['mp4', 'webm', 'mov', 'mkv', 'm4v'].includes(ext)) {
      setError('Format video harus MP4, WebM, MOV, atau MKV.');
      return;
    }
    setError(null);
    setVideoFile(file);
    setTitle((prev) => (prev.trim() ? prev : file.name.replace(/\.[^.]+$/, '')));
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setIsDragging(false);
      const dropped = e.dataTransfer.files?.[0] || null;
      handlePickVideoFile(dropped);
    },
    [handlePickVideoFile]
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!videoFile) {
      setError('Pilih atau seret file video terlebih dahulu.');
      return;
    }
    if (title.trim().length < 2) {
      setError('Judul video minimal 2 karakter.');
      return;
    }

    setError(null);
    setIsUploading(true);
    setUploadPct(0);

    try {
      const fd = new FormData();
      fd.append('file', videoFile);
      fd.append('title', title.trim());
      fd.append('description', description.trim());
      fd.append('category', category);
      fd.append('tags', tags.trim());
      fd.append('is_short', String(isShort));
      if (thumbFile) {
        fd.append('thumbnail', thumbFile);
      }

      const res = await videosApi.upload(fd, (pct) => setUploadPct(pct));
      setUploadPct(100);
      resetForm();
      onUploaded(res.video, res.message);
      onClose();
    } catch (err) {
      setError(extractApiError(err, 'Gagal mengupload video ke server Python.'));
    } finally {
      setIsUploading(false);
    }
  };

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-3 sm:p-4"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="vt-upload-title"
    >
      <div
        className="vt-fade-scale flex max-h-[92vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border shadow-2xl"
        style={{
          backgroundColor: 'var(--vt-surface)',
          borderColor: 'var(--vt-border)',
          color: 'var(--vt-text-primary)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Studio Header */}
        <div
          className="flex items-center justify-between border-b px-5 py-4"
          style={{ borderColor: 'var(--vt-border)' }}
        >
          <div>
            <h2 id="vt-upload-title" className="text-lg font-bold">
              Upload video — VeloxTube Studio
            </h2>
            <p className="text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
              Otomatis dikonversi ke HLS adaptif / Byte-Range streaming di backend Python
            </p>
          </div>
          <button
            type="button"
            disabled={isUploading}
            onClick={handleClose}
            aria-label="Tutup modal upload"
            className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full disabled:opacity-40"
            style={{ backgroundColor: 'var(--vt-chip)' }}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Scrollable Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 space-y-4 overflow-y-auto p-5">
          {error && (
            <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-2.5 text-xs font-medium text-red-400">
              {error}
            </div>
          )}

          {/* Drag & Drop Video Area */}
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setIsDragging(true);
            }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
            onClick={() => !isUploading && videoInputRef.current?.click()}
            className={`flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-6 text-center transition-colors ${
              isDragging ? 'border-[#FF0000] bg-[#FF0000]/10' : ''
            }`}
            style={{
              borderColor: isDragging ? '#FF0000' : 'var(--vt-border)',
              backgroundColor: isDragging ? undefined : 'var(--vt-bg)',
            }}
          >
            <input
              ref={videoInputRef}
              type="file"
              accept=".mp4,.webm,.mov,.mkv,.m4v,video/*"
              className="hidden"
              onChange={(e) => handlePickVideoFile(e.target.files?.[0] || null)}
            />
            {videoFile ? (
              <div className="flex items-center gap-3">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-400">
                  <CheckCircle2 className="h-6 w-6" />
                </span>
                <div className="text-left">
                  <p className="max-w-xs truncate text-sm font-semibold sm:max-w-md">
                    {videoFile.name}
                  </p>
                  <p className="text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
                    Ukuran: {formatFileSize(videoFile.size)} • Klik untuk ganti file
                  </p>
                </div>
              </div>
            ) : (
              <>
                <span
                  className="flex h-14 w-14 items-center justify-center rounded-full"
                  style={{ backgroundColor: 'var(--vt-chip)' }}
                >
                  <UploadCloud className="h-7 w-7" />
                </span>
                <p className="mt-3 text-sm font-semibold">
                  Tarik dan lepas file video untuk diupload
                </p>
                <p className="mt-1 text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
                  Mendukung .mp4, .webm, .mov, .mkv (maks. 500 MB)
                </p>
                <span className="mt-3 inline-flex rounded-full bg-[#F1F1F1] px-4 py-1.5 text-xs font-semibold text-[#0F0F0F]">
                  Pilih File Video
                </span>
              </>
            )}
          </div>

          {/* Video Format Toggle: Regular vs Shorts */}
          <div className="grid grid-cols-2 gap-2.5">
            <button
              type="button"
              onClick={() => setIsShort(false)}
              className="flex cursor-pointer items-center justify-center gap-2 rounded-xl border px-3 py-2.5 text-sm font-medium transition-colors"
              style={{
                backgroundColor: !isShort ? 'var(--vt-chip)' : 'transparent',
                borderColor: !isShort ? '#FF0000' : 'var(--vt-border)',
              }}
            >
              <Film className="h-4 w-4" />
              <span>Video Biasa (16:9)</span>
            </button>
            <button
              type="button"
              onClick={() => setIsShort(true)}
              className="flex cursor-pointer items-center justify-center gap-2 rounded-xl border px-3 py-2.5 text-sm font-medium transition-colors"
              style={{
                backgroundColor: isShort ? 'var(--vt-chip)' : 'transparent',
                borderColor: isShort ? '#FF0000' : 'var(--vt-border)',
              }}
            >
              <Smartphone className="h-4 w-4" />
              <span>Shorts (Vertikal 9:16)</span>
            </button>
          </div>

          {/* Title */}
          <div>
            <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
              Judul Video (wajib)
            </label>
            <input
              type="text"
              required
              minLength={2}
              maxLength={180}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Tambahkan judul yang menjelaskan video Anda"
              className="w-full rounded-xl border px-3.5 py-2.5 text-sm outline-none"
              style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
            />
          </div>

          {/* Description */}
          <div>
            <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
              Deskripsi
            </label>
            <textarea
              rows={3}
              maxLength={5000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Ceritakan tentang video Anda kepada penonton..."
              className="w-full resize-none rounded-xl border px-3.5 py-2.5 text-sm outline-none"
              style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
            />
          </div>

          {/* Category + Tags */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
                Kategori
              </label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full rounded-xl border px-3 py-2.5 text-sm outline-none"
                style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
              >
                {UPLOAD_CATEGORIES.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
                Tag (pisahkan dengan koma)
              </label>
              <input
                type="text"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="tutorial, 60fps, gaming"
                className="w-full rounded-xl border px-3.5 py-2.5 text-sm outline-none"
                style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
              />
            </div>
          </div>

          {/* Optional Custom Thumbnail */}
          <div>
            <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
              Thumbnail Kustom (opsional — backend otomatis membuat thumbnail jika kosong)
            </label>
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => thumbInputRef.current?.click()}
                className="flex h-20 w-36 shrink-0 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-dashed text-xs"
                style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
              >
                {thumbPreviewUrl ? (
                  <img
                    src={thumbPreviewUrl}
                    alt="Preview thumbnail"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <>
                    <ImagePlus className="h-5 w-5 opacity-70" />
                    <span className="mt-1 opacity-80">Upload gambar</span>
                  </>
                )}
              </button>
              <input
                ref={thumbInputRef}
                type="file"
                accept=".jpg,.jpeg,.png,.webp,image/*"
                className="hidden"
                onChange={(e) => setThumbFile(e.target.files?.[0] || null)}
              />
              {thumbFile && (
                <div className="min-w-0 text-xs">
                  <p className="truncate font-medium">{thumbFile.name}</p>
                  <button
                    type="button"
                    onClick={() => setThumbFile(null)}
                    className="mt-1 cursor-pointer text-red-400 hover:underline"
                  >
                    Hapus thumbnail
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Real-Time Upload Progress Bar */}
          {isUploading && (
            <div className="space-y-1.5 rounded-xl p-3" style={{ backgroundColor: 'var(--vt-bg)' }}>
              <div className="flex items-center justify-between text-xs font-medium">
                <span className="flex items-center gap-2">
                  <Loader2 className="h-3.5 w-3.5 animate-spin text-[#FF0000]" />
                  {uploadPct < 100
                    ? 'Mengupload video ke backend Python...'
                    : 'Menyelesaikan pemrosesan server...'}
                </span>
                <span className="tabular-nums font-bold">{uploadPct}%</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-white/15">
                <div
                  className="h-full bg-[#FF0000] transition-transform duration-150"
                  style={{
                    width: '100%',
                    transform: `scaleX(${uploadPct / 100})`,
                    transformOrigin: 'left',
                  }}
                />
              </div>
            </div>
          )}

          {/* Footer Buttons */}
          <div
            className="flex items-center justify-end gap-2.5 border-t pt-4"
            style={{ borderColor: 'var(--vt-border)' }}
          >
            <button
              type="button"
              disabled={isUploading}
              onClick={handleClose}
              className="cursor-pointer rounded-full px-4 py-2 text-sm font-medium disabled:opacity-40"
              style={{ backgroundColor: 'var(--vt-chip)' }}
            >
              Batal
            </button>
            <button
              type="submit"
              disabled={isUploading || !videoFile}
              className="flex cursor-pointer items-center gap-2 rounded-full bg-[#FF0000] px-5 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              {isUploading && <Loader2 className="h-4 w-4 animate-spin" />}
              <span>{isUploading ? `Mengupload (${uploadPct}%)` : 'Publikasikan'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

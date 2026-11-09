import styles from './StatusBadge.module.scss';

interface StatusBadgeProps {
  status: string;
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const label = status.trim() === '' ? 'UNKNOWN' : status.trim().toUpperCase();
  const tone = label.toLowerCase();

  return (
    <span
      className={`${styles.badge} ${styles[tone] ?? styles.default}`}
      data-status={tone}
    >
      {label}
    </span>
  );
}

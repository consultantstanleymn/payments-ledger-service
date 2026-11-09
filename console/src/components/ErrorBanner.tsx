import styles from './ErrorBanner.module.scss';

interface ErrorBannerProps {
  message: string | null;
}

export default function ErrorBanner({ message }: ErrorBannerProps) {
  if (!message) {
    return null;
  }

  return (
    <div className={styles.banner} role="alert">
      {message}
    </div>
  );
}


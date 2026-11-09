import styles from './LoadMore.module.scss';

interface LoadMoreProps {
  nextCursor: string | null;
  loading: boolean;
  onClick: () => void;
}

export default function LoadMore({ nextCursor, loading, onClick }: LoadMoreProps) {
  return (
    <div className={styles.wrap}>
      <button
        type="button"
        className={styles.button}
        onClick={onClick}
        disabled={!nextCursor || loading}
      >
        {loading ? 'Loading...' : 'Load more'}
      </button>
    </div>
  );
}


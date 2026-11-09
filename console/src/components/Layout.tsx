import { NavLink, Outlet } from 'react-router-dom';
import styles from './Layout.module.scss';

export default function Layout() {
  return (
    <div className={styles.appShell}>
      <a className={styles.skipLink} href="#main-content">
        Skip to content
      </a>
      <header className={styles.header}>
        <span className={styles.logo}>Ledger console</span>
        <nav aria-label="Primary" className={styles.nav}>
          <NavLink
            to="/accounts"
            className={({ isActive }) =>
              `${styles.navLink} ${isActive ? styles.navLinkActive : ''}`
            }
          >
            Accounts
          </NavLink>
          <NavLink
            to="/payments"
            className={({ isActive }) =>
              `${styles.navLink} ${isActive ? styles.navLinkActive : ''}`
            }
          >
            Payments
          </NavLink>
          <NavLink
            to="/operations"
            className={({ isActive }) =>
              `${styles.navLink} ${isActive ? styles.navLinkActive : ''}`
            }
          >
            Operations
          </NavLink>
        </nav>
      </header>
      <main id="main-content" className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}


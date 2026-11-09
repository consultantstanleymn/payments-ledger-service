import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from './StatusBadge';

describe('StatusBadge', () => {
  it('badge has text label', () => {
    render(<StatusBadge status="CAPTURED" />);

    expect(screen.getByText('CAPTURED')).toBeInTheDocument();
  });

  it('shows unknown status as text too', () => {
    render(<StatusBadge status="PENDING" />);

    expect(screen.getByText('PENDING')).toBeInTheDocument();
  });
});

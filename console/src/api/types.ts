export interface AccountSummary {
  id: string;
  name: string;
  currency: string;
  postedMinor: number;
  heldMinor: number;
  availableMinor: number;
  floorMinor: number;
  createdAt: string;
}

export interface OpsPage<T> {
  items: T[];
  nextCursor: string | null;
}

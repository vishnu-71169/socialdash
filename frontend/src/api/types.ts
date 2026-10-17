export type Platform = 'FACEBOOK' | 'INSTAGRAM' | 'TWITTER' | 'LINKEDIN';
export const PLATFORMS: Platform[] = ['INSTAGRAM', 'FACEBOOK', 'TWITTER', 'LINKEDIN'];
export const PLATFORM_LIMITS: Record<Platform, number> = {
  FACEBOOK: 63206,
  INSTAGRAM: 2200,
  TWITTER: 280,
  LINKEDIN: 3000,
};

export interface User {
  id: number;
  name: string;
  email: string;
  role: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Account {
  id: number;
  platform: Platform;
  handle: string;
  displayName: string;
  simulated: boolean;
  connectedAt: string;
  lastSyncedAt: string | null;
  followers: number | null;
}

export interface Metric {
  value: number;
  previous: number;
  changePct: number | null;
}

export interface Overview {
  startDate: string;
  endDate: string;
  accountCount: number;
  publishedPosts: number;
  followers: Metric;
  followerGrowth: Metric;
  impressions: Metric;
  likes: Metric;
  comments: Metric;
  shares: Metric;
  engagementRate: Metric;
}

export interface Breakdown {
  key: string;
  accountId: number | null;
  platform: Platform;
  label: string;
  accountCount: number;
  followers: number;
  followerGrowth: number;
  impressions: number;
  likes: number;
  comments: number;
  shares: number;
  engagementRate: number;
}

export interface TrendPoint {
  date: string;
  followers: number;
  impressions: number;
  likes: number;
  comments: number;
  shares: number;
  engagements: number;
  engagementRate: number;
}

export interface PostPerformance {
  publicationId: number;
  postId: number;
  content: string;
  platform: Platform;
  accountHandle: string;
  publishedAt: string;
  externalUrl: string | null;
  impressions: number;
  likes: number;
  comments: number;
  shares: number;
  engagements: number;
  engagementRate: number;
}

export interface PostHistoryPoint {
  recordedAt: string;
  impressions: number;
  likes: number;
  comments: number;
  shares: number;
}

export type PostStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHING' | 'PUBLISHED' | 'FAILED' | 'CANCELLED';
export type PublicationStatus = 'PENDING' | 'SCHEDULED' | 'PUBLISHING' | 'PUBLISHED' | 'FAILED' | 'CANCELLED';
export type PostAction = 'DRAFT' | 'SCHEDULE' | 'PUBLISH_NOW';

export interface Publication {
  id: number;
  accountId: number;
  platform: Platform;
  accountHandle: string;
  status: PublicationStatus;
  attemptCount: number;
  nextAttemptAt: string | null;
  publishedAt: string | null;
  externalUrl: string | null;
  lastError: string | null;
  impressions: number;
  likes: number;
  comments: number;
  shares: number;
}

export interface Post {
  id: number;
  content: string;
  mediaUrl: string | null;
  status: PostStatus;
  partiallyPublished: boolean;
  scheduledAt: string | null;
  createdAt: string;
  updatedAt: string;
  publications: Publication[];
}

export interface PostRequest {
  content: string;
  mediaUrl?: string | null;
  accountIds: number[];
  scheduledAt?: string | null;
  action: PostAction;
}

export interface Keyword {
  id: number;
  keyword: string;
  platform: Platform | null;
  createdAt: string;
  mentionsLast24h: number;
  mentionsLast7d: number;
}

export interface Mention {
  id: number;
  keywordId: number;
  keyword: string;
  platform: Platform;
  authorHandle: string;
  content: string;
  url: string | null;
  detectedAt: string;
}

export interface Volume {
  dates: string[];
  series: { keywordId: number; keyword: string; counts: number[]; total: number; previousTotal: number | null }[];
}

export interface TermCount {
  term: string;
  count: number;
}

export type EngagementType = 'COMMENT' | 'MENTION' | 'DIRECT_MESSAGE';
export type EngagementStatus = 'OPEN' | 'REPLIED' | 'RESOLVED';

export interface Engagement {
  id: number;
  accountId: number;
  platform: Platform;
  accountHandle: string;
  type: EngagementType;
  status: EngagementStatus;
  authorName: string;
  authorHandle: string;
  content: string;
  receivedAt: string;
  replyContent: string | null;
  repliedAt: string | null;
  resolvedAt: string | null;
}

export interface EngagementSummary {
  counts: Record<EngagementStatus, number>;
  avgResponseMinutes: number | null;
}

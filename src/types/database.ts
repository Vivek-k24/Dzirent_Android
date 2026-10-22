export type UserRole = 'renter' | 'lender' | 'both';

export type ListingStatus = 'active' | 'paused';

export type MeasurementUnit = 'cm' | 'in';

export type ReportTargetType = 'listing' | 'user' | 'message';

export interface Profile {
  id: string;
  display_name: string;
  role: UserRole;
  city: string;
  approx_lat: number;
  approx_lng: number;
  adult_confirmed: boolean;
  created_at: string;
}

export interface Listing {
  id: string;
  lender_id: string;
  title: string;
  category: string;
  size_label: string;
  description: string;
  price_note: string;
  city: string;
  approx_lat: number;
  approx_lng: number;
  status: ListingStatus;
  created_at: string;
  // joined fields
  photos?: ListingPhoto[];
  measurements?: ListingMeasurement[];
  availability?: Availability[];
  lender?: Profile;
  distance_km?: number;
}

export interface ListingPhoto {
  id: string;
  listing_id: string;
  storage_path: string;
  position: number;
}

export interface ListingMeasurement {
  id: string;
  listing_id: string;
  label: string;
  value: number;
  unit: MeasurementUnit;
}

export interface Availability {
  id: string;
  listing_id: string;
  start_date: string;
  end_date: string;
}

export interface Conversation {
  id: string;
  listing_id: string;
  renter_id: string;
  lender_id: string;
  created_at: string;
  // joined fields
  listing?: Listing;
  other_user?: Profile;
  last_message?: Message;
}

export interface Message {
  id: string;
  conversation_id: string;
  sender_id: string;
  body: string;
  created_at: string;
}

export interface Block {
  blocker_id: string;
  blocked_id: string;
}

export interface Report {
  id?: string;
  reporter_id: string;
  target_type: ReportTargetType;
  target_id: string;
  reason: string;
  created_at?: string;
}

export interface RentalOutcome {
  conversation_id: string;
  user_id: string;
  happened: boolean;
  answered_at: string;
}

export const CATEGORIES = [
  'Career & Professional',
  'Occasion & Party',
  'Cultural & Traditional',
  'Everyday',
  'Outerwear',
  'Bags & Accessories',
] as const;

export type Category = (typeof CATEGORIES)[number];

export const CATEGORY_MEASUREMENT_SUGGESTIONS: Record<string, string[]> = {
  'Career & Professional': ['Shoulder', 'Chest', 'Sleeve', 'Length', 'Waist'],
  'Occasion & Party': ['Bust', 'Waist', 'Hips', 'Total Length', 'Shoulder'],
  'Cultural & Traditional': ['Chest/Bust', 'Waist', 'Kurta/Top Length', 'Bottom Length', 'Shoulder'],
  'Everyday': ['Chest', 'Length', 'Waist', 'Inseam'],
  'Outerwear': ['Chest', 'Shoulder', 'Sleeve Length', 'Total Length'],
  'Bags & Accessories': ['Width', 'Height', 'Depth', 'Strap Drop'],
};

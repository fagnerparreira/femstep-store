export interface ShippingAddress {
  recipientName: string;
  postalCode: string;
  street: string;
  number: string;
  complement?: string;
  neighborhood: string;
  city: string;
  state: string;
}

export interface CheckoutPreview {
  subtotal: number;
  discount: number;
  shipping: number;
  total: number;
  shippingService: string;
  estimatedDays: number;
  appliedCoupon?: string;
}

export interface OrderItem {
  sku: string;
  productName: string;
  productSlug: string;
  imageUrl: string;
  size: number;
  color: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Order {
  orderNumber: string;
  status: string;
  paymentStatus: string;
  paymentMethod: 'PIX' | 'SANDBOX_CARD';
  subtotal: number;
  discount: number;
  shipping: number;
  total: number;
  shippingService: string;
  estimatedDays: number;
  pixCode?: string;
  createdAt: string;
  items: OrderItem[];
}

export interface CheckoutRequest {
  address: ShippingAddress;
  couponCode?: string;
  paymentMethod: 'PIX' | 'SANDBOX_CARD';
  idempotencyKey: string;
  notes?: string;
}

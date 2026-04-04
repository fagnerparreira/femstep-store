export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productSlug: string;
  productImage: string;
  price: number;
  quantity: number;
  size?: number;
  color?: string;
  stock: number;
  addedAt: Date;
}

export interface Cart {
  id: number;
  items: CartItem[];
  totalAmount: number;
  totalItems: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface AddToCartRequest {
  productId: number;
  quantity: number;
  size?: number;
  color?: string;
}

export interface UpdateCartRequest {
  itemId: number;
  quantity: number;
}

export interface CartSummary {
  subtotal: number;
  shipping: number;
  discount: number;
  total: number;
}

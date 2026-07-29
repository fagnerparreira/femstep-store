export interface Product {
  id: number;
  name: string;
  slug: string;
  description?: string;
  shortDescription?: string;
  price: number;
  originalPrice?: number;
  imageUrl: string;
  imageAlt?: string;
  category: string;
  categorySlug: string;
  material?: string;
  brand?: string;
  featured: boolean;
  active: boolean;
  isNew?: boolean;
  onSale?: boolean;
  availableSizes: number[];
  availableColors: string[];
  stock: number;
  variants?: ProductVariant[];
  images?: ProductImage[];
  createdAt?: Date;
  updatedAt?: Date;
}

export interface ProductVariant {
  id: number;
  productId?: number;
  sku: string;
  size: number;
  color: string;
  stock?: number;
  availableStock: number;
  price: number;
  priceOverride?: number;
  active: boolean;
}

export interface ProductImage {
  id: number;
  productId: number;
  url: string;
  altText: string;
  displayOrder: number;
  mainImage: boolean;
}

export interface ProductFilter {
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  size?: number;
  color?: string;
  featured?: boolean;
  active?: boolean;
  search?: string;
}

export interface ProductSearchResult {
  products: Product[];
  totalCount: number;
  currentPage: number;
  totalPages: number;
  pageSize: number;
  first: boolean;
  last: boolean;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description?: string;
}

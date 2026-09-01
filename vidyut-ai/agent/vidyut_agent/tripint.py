from pydantic import BaseModel
from typing import Optional

class TripIntent(BaseModel):
    origin: Optional[str] = None
    destination: Optional[str] = None

    current_battery_percent: Optional[float] = None
    reserve_battery_percent: Optional[float] = None

    max_budget: Optional[float] = None
    arrive_by: Optional[str] = None

    optimization_mode: Optional[str] = None
    trip_purpose: Optional[str] = None